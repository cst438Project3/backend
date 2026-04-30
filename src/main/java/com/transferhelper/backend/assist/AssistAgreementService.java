package com.transferhelper.backend.assist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transferhelper.backend.institutions.InstitutionCodeDto;
import com.transferhelper.backend.institutions.InstitutionCodeService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AssistAgreementService {

	private static final String BASE_URL = "https://assist.org";
	private static final List<String> AGREEMENT_PREFIXES = List.of("AD", "AM", "AP");
	private static final Map<String, String> CATEGORY_BY_PREFIX = Map.of(
			"AD", "dept",
			"AM", "major",
			"AP", "prefix"
	);
	private static final Map<String, String> ALL_SUFFIX_BY_PREFIX = Map.of(
			"AD", "AllDepartments",
			"AM", "AllMajors",
			"AP", "AllPrefixes"
	);
	private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("[A-Z]{2,8}\\s+\\d{1,4}[A-Z]?");

	private final ObjectMapper objectMapper;
	private final InstitutionCodeService institutionCodeService;

	public AssistAgreementService(InstitutionCodeService institutionCodeService) {
		this.objectMapper = new ObjectMapper();
		this.institutionCodeService = institutionCodeService;
	}

	public Map<String, Object> fetchSinglePair(int year, long toId, long fromId) {
		AssistHttpClient client = new AssistHttpClient(objectMapper);

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("year", year);
		meta.put("to_id", toId);
		meta.put("from_id", fromId);
		meta.put("generated_at", OffsetDateTime.now().toString());
		meta.put("to_institution", resolveInstitutionName(toId));
		meta.put("from_institution", resolveInstitutionName(fromId));

		Map<String, Object> agreements = new LinkedHashMap<>();
		Map<String, List<Map<String, Object>>> bySendingCourse = new TreeMap<>();
		Map<String, List<Map<String, Object>>> byReceivingRequirement = new TreeMap<>();
		Map<String, List<String>> byType = new LinkedHashMap<>();
		Map<String, Object> fetchStatus = new LinkedHashMap<>();

		int totalRows = 0;
		int articulated = 0;
		int nonArticulated = 0;

		for (String prefix : AGREEMENT_PREFIXES) {
			AgreementFetchResult fetchResult = client.fetchAgreement(year, toId, fromId, prefix);
			fetchStatus.put(prefix, fetchResult.status());

			Map<String, Object> agreementNode = new LinkedHashMap<>();
			agreementNode.put("status", fetchResult.status());
			agreementNode.put("key", fetchResult.key());

			if (fetchResult.payload() == null) {
				agreementNode.put("row_count", 0);
				agreementNode.put("mappings", List.of());
				agreements.put(prefix, agreementNode);
				byType.put(prefix, List.of());
				continue;
			}

			ParsedAgreement parsed = parseAgreementPayload(fetchResult.payload(), prefix);
			agreementNode.put("agreement_type", parsed.agreementType());
			agreementNode.put("agreement_name", parsed.agreementName());
			agreementNode.put("publish_date", parsed.publishDate());
			agreementNode.put("row_count", parsed.rows().size());
			agreementNode.put("mappings", parsed.rows());
			agreements.put(prefix, agreementNode);

			List<String> typeRowIds = new ArrayList<>();
			for (Map<String, Object> row : parsed.rows()) {
				totalRows++;
				String rowId = String.valueOf(row.get("row_id"));
				typeRowIds.add(rowId);

				boolean hasNoReason = !asText(row.get("no_articulation_reason")).isBlank();
				@SuppressWarnings("unchecked")
				List<String> options = (List<String>) row.getOrDefault("sending_course_options", List.of());
				if (hasNoReason || options.isEmpty()) {
					nonArticulated++;
				} else {
					articulated++;
				}

				String receivingRequirement = asText(row.get("receiving_requirement"));
				if (!receivingRequirement.isBlank()) {
					byReceivingRequirement
							.computeIfAbsent(receivingRequirement, ignored -> new ArrayList<>())
							.add(ref(prefix, rowId, receivingRequirement, asText(row.get("sending_courses"))));
				}

				for (String code : extractSendingCourseCodes(row)) {
					bySendingCourse
							.computeIfAbsent(code, ignored -> new ArrayList<>())
							.add(ref(prefix, rowId, receivingRequirement, asText(row.get("sending_courses"))));
				}
			}
			byType.put(prefix, typeRowIds);
		}

		meta.put("fetch_status", fetchStatus);

		Map<String, Object> searchIndex = new LinkedHashMap<>();
		searchIndex.put("by_sending_course", bySendingCourse);
		searchIndex.put("by_receiving_requirement", byReceivingRequirement);
		searchIndex.put("by_type", byType);

		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("total_rows", totalRows);
		summary.put("articulated_rows", articulated);
		summary.put("non_articulated_rows", nonArticulated);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("meta", meta);
		payload.put("agreements", agreements);
		payload.put("search_index", searchIndex);
		payload.put("summary", summary);
		return payload;
	}

	private ParsedAgreement parseAgreementPayload(JsonNode payload, String prefix) {
		JsonNode result = payload.path("result");
		JsonNode receivingInst = parseEmbeddedJson(result.get("receivingInstitution"));
		JsonNode sendingInst = parseEmbeddedJson(result.get("sendingInstitution"));
		JsonNode yearInfo = parseEmbeddedJson(result.get("academicYear"));

		String receivingName = firstName(receivingInst);
		String sendingName = firstName(sendingInst);
		String academicYear = asText(yearInfo.get("code"));
		String agreementType = asText(result.get("type"));
		String agreementName = asText(result.get("name"));
		String publishDate = asText(result.get("publishDate"));

		JsonNode templateAssets = parseEmbeddedJson(result.get("templateAssets"));
		JsonNode articulations = parseEmbeddedJson(result.get("articulations"));

		Map<String, Map<String, String>> templateIndex = buildTemplateIndex(templateAssets);

		List<Map<String, Object>> rows = new ArrayList<>();
		Map<String, Boolean> dedupe = new HashMap<>();
		int[] rowCounter = {0};

		boolean templateShape = articulations.isArray() && articulations.size() > 0
				&& articulations.get(0).isObject() && articulations.get(0).has("templateCellId");

		if (articulations.isArray()) {
			if (templateShape) {
				for (JsonNode entry : articulations) {
					String cellId = asText(entry.get("templateCellId"));
					JsonNode articulation = entry.path("articulation");
					Map<String, String> template = templateIndex.getOrDefault(cellId, Map.of());
					addRow(rows, dedupe, rowCounter, prefix, academicYear, receivingName, sendingName, cellId,
							articulation, template, "");
				}
			} else {
				for (JsonNode bucket : articulations) {
					if (!bucket.isObject()) {
						continue;
					}
					String category = asText(bucket.get("name"));
					JsonNode nested = bucket.get("articulations");
					if (nested != null && nested.isArray()) {
						for (JsonNode articulation : nested) {
							if (!articulation.isObject()) {
								continue;
							}
							addRow(rows, dedupe, rowCounter, prefix, academicYear, receivingName, sendingName, "",
									articulation, Map.of(), category);
						}
					}
				}
			}
		}

		return new ParsedAgreement(agreementType, agreementName, publishDate, rows);
	}

	private void addRow(
			List<Map<String, Object>> rows,
			Map<String, Boolean> dedupe,
			int[] rowCounter,
			String prefix,
			String academicYear,
			String receivingInstitution,
			String sendingInstitution,
			String cellId,
			JsonNode articulation,
			Map<String, String> template,
			String categoryName
	) {
		JsonNode sending = articulation.path("sendingArticulation");
		SendingDetails sendingDetails = buildSendingDetails(sending);
		String receivingDesc = receivingLabel(articulation);
		if (receivingDesc.isBlank()) {
			receivingDesc = template.getOrDefault("receiving_label", "");
		}

		Map<String, Object> row = new LinkedHashMap<>();
		rowCounter[0]++;
		row.put("row_id", prefix + "_" + rowCounter[0]);
		row.put("agreement_type", prefix);
		row.put("academic_year", academicYear);
		row.put("sending_institution", sendingInstitution);
		row.put("receiving_institution", receivingInstitution);
		row.put("major", defaultIfBlank(template.get("major"), categoryName));
		row.put("group", defaultIfBlank(template.get("group"), ""));
		row.put("section", defaultIfBlank(template.get("section"), ""));
		row.put("category", defaultIfBlank(categoryName, ""));
		row.put("receiving_type", defaultIfBlank(template.get("receiving_type"), asText(articulation.get("type"))));
		row.put("receiving_requirement", receivingDesc);
		row.put("sending_courses", sendingDetails.sendingCourses());
		row.put("sending_course_options", sendingDetails.sendingCourseOptions());
		row.put("fulfillment_options", sendingDetails.fulfillmentOptions());
		row.put("no_articulation_reason", sendingDetails.noArticulationReason());
		row.put("denied_courses", sendingDetails.deniedCourses());
		row.put("template_cell_id", cellId);

		String dedupeKey = String.join("|",
				asText(row.get("academic_year")),
				asText(row.get("sending_institution")),
				asText(row.get("receiving_institution")),
				asText(row.get("major")),
				asText(row.get("category")),
				asText(row.get("receiving_requirement")),
				asText(row.get("sending_courses"))
		);
		if (dedupe.containsKey(dedupeKey)) {
			return;
		}
		dedupe.put(dedupeKey, true);
		rows.add(row);
	}

	private SendingDetails buildSendingDetails(JsonNode sending) {
		String reason = asText(sending.get("noArticulationReason"));
		List<String> denied = deniedCourses(sending);
		if (!reason.isBlank()) {
			return new SendingDetails(
					"No articulation (" + reason + ")",
					List.of(),
					List.of(),
					reason,
					denied
			);
		}

		List<JsonNode> groups = sortedByPosition(sending.path("items"));
		Map<String, String> betweenConjunction = groupConjunctionMap(sending.path("courseGroupConjunctions"));

		List<String> renderedGroups = new ArrayList<>();
		for (JsonNode group : groups) {
			String rendered = renderGroup(group);
			if (!rendered.isBlank()) {
				renderedGroups.add(rendered);
			}
		}

		String sendingCourses = renderSendingArticulation(renderedGroups, betweenConjunction, denied);
		List<String> options = splitOrOptions(renderedGroups, betweenConjunction);
		List<Map<String, Object>> fulfillment = buildFulfillmentOptions(groups, betweenConjunction);

		return new SendingDetails(sendingCourses, options, fulfillment, "", denied);
	}

	private List<Map<String, Object>> buildFulfillmentOptions(List<JsonNode> groups, Map<String, String> betweenConjunction) {
		List<List<Map<String, Object>>> optionGroups = new ArrayList<>();
		for (int i = 0; i < groups.size(); i++) {
			JsonNode group = groups.get(i);
			List<String> courses = new ArrayList<>();
			for (JsonNode item : safeArray(group.get("items"))) {
				if ("Course".equals(asText(item.get("type")))) {
					String label = courseLabel(item);
					if (!label.isBlank()) {
						courses.add(label);
					}
				}
			}
			if (courses.isEmpty()) {
				continue;
			}

			Map<String, Object> groupPayload = new LinkedHashMap<>();
			groupPayload.put("group_conjunction", defaultIfBlank(asText(group.get("courseConjunction")).toUpperCase(), "AND"));
			groupPayload.put("courses", courses);

			if (optionGroups.isEmpty()) {
				optionGroups.add(new ArrayList<>(List.of(groupPayload)));
				continue;
			}

			String between = defaultIfBlank(betweenConjunction.get((i - 1) + ":" + i), "AND").toUpperCase();
			if ("OR".equals(between)) {
				optionGroups.add(new ArrayList<>(List.of(groupPayload)));
			} else {
				List<List<Map<String, Object>>> expanded = new ArrayList<>();
				for (List<Map<String, Object>> option : optionGroups) {
					List<Map<String, Object>> copy = new ArrayList<>(option);
					copy.add(groupPayload);
					expanded.add(copy);
				}
				optionGroups = expanded;
			}
		}

		List<Map<String, Object>> options = new ArrayList<>();
		for (int i = 0; i < optionGroups.size(); i++) {
			List<Map<String, Object>> grouped = optionGroups.get(i);
			List<String> flattened = new ArrayList<>();
			for (Map<String, Object> g : grouped) {
				@SuppressWarnings("unchecked")
				List<String> courses = (List<String>) g.get("courses");
				flattened.addAll(courses);
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("option", i + 1);
			payload.put("groups", grouped);
			payload.put("courses", flattened);
			payload.put("must_take_together", flattened.size() > 1);
			options.add(payload);
		}

		return options;
	}

	private List<String> splitOrOptions(List<String> renderedGroups, Map<String, String> betweenConjunction) {
		if (renderedGroups.isEmpty()) {
			return List.of();
		}
		List<String> options = new ArrayList<>();
		options.add(renderedGroups.get(0));
		for (int i = 1; i < renderedGroups.size(); i++) {
			String current = renderedGroups.get(i);
			String conjunction = defaultIfBlank(betweenConjunction.get((i - 1) + ":" + i), "AND").toUpperCase();
			if ("OR".equals(conjunction)) {
				options.add(current);
			} else {
				List<String> merged = new ArrayList<>();
				for (String option : options) {
					merged.add("(" + option + ") AND (" + current + ")");
				}
				options = merged;
			}
		}
		return options;
	}

	private String renderSendingArticulation(List<String> renderedGroups, Map<String, String> betweenConjunction, List<String> denied) {
		if (renderedGroups.isEmpty()) {
			return denied.isEmpty() ? "" : "Not accepted: " + String.join(", ", denied);
		}
		String out = renderedGroups.get(0);
		for (int i = 1; i < renderedGroups.size(); i++) {
			String conjunction = defaultIfBlank(betweenConjunction.get((i - 1) + ":" + i), "AND").toUpperCase();
			out = "(" + out + ") " + conjunction + " (" + renderedGroups.get(i) + ")";
		}
		if (!denied.isEmpty()) {
			out += " | Not accepted: " + String.join(", ", denied);
		}
		return out;
	}

	private String renderGroup(JsonNode group) {
		List<String> parts = new ArrayList<>();
		for (JsonNode item : safeArray(group.get("items"))) {
			if ("Course".equals(asText(item.get("type")))) {
				String label = courseLabel(item);
				if (!label.isBlank()) {
					parts.add(label);
				}
			}
		}
		if (parts.isEmpty()) {
			return "";
		}
		String conjunction = defaultIfBlank(asText(group.get("courseConjunction")), "And").toUpperCase();
		return String.join(" " + conjunction + " ", parts);
	}

	private Map<String, String> groupConjunctionMap(JsonNode conjunctions) {
		Map<String, String> map = new HashMap<>();
		for (JsonNode c : safeArray(conjunctions)) {
			int begin = c.path("sendingCourseGroupBeginPosition").asInt(-1);
			int end = c.path("sendingCourseGroupEndPosition").asInt(-1);
			if (begin >= 0 && end >= 0) {
				map.put(begin + ":" + end, defaultIfBlank(asText(c.get("groupConjunction")), "And"));
			}
		}
		return map;
	}

	private List<JsonNode> sortedByPosition(JsonNode nodes) {
		List<JsonNode> list = new ArrayList<>();
		for (JsonNode n : safeArray(nodes)) {
			list.add(n);
		}
		list.sort(Comparator.comparingInt(n -> n.path("position").asInt(0)));
		return list;
	}

	private List<String> deniedCourses(JsonNode sending) {
		List<String> denied = new ArrayList<>();
		for (JsonNode node : safeArray(sending.get("deniedCourses"))) {
			String label = courseLabel(node);
			if (!label.isBlank()) {
				denied.add(label);
			}
		}
		return denied;
	}

	private Map<String, Map<String, String>> buildTemplateIndex(JsonNode templateAssets) {
		Map<String, Map<String, String>> index = new HashMap<>();
		walkTemplate(index, templateAssets, "", "", "");
		return index;
	}

	private void walkTemplate(Map<String, Map<String, String>> index, JsonNode node, String major, String group, String section) {
		if (node == null || node.isNull()) {
			return;
		}
		if (node.isArray()) {
			for (JsonNode item : node) {
				walkTemplate(index, item, major, group, section);
			}
			return;
		}
		if (!node.isObject()) {
			return;
		}

		String nextMajor = major;
		if (node.has("name") && node.has("templateAssets")) {
			nextMajor = asText(node.get("name"));
		}

		String nextGroup = group;
		String nextSection = section;
		String nodeType = asText(node.get("type"));
		if ("RequirementGroup".equals(nodeType)) {
			nextGroup = asText(node.get("instruction"));
		} else if ("RequirementSection".equals(nodeType)) {
			nextSection = defaultIfBlank(asText(node.get("name")), asText(node.get("instruction")));
		}

		for (JsonNode cell : safeArray(node.get("cells"))) {
			String id = asText(cell.get("id"));
			if (id.isBlank()) {
				continue;
			}
			Map<String, String> payload = new LinkedHashMap<>();
			payload.put("major", nextMajor);
			payload.put("group", nextGroup);
			payload.put("section", nextSection);
			payload.put("receiving_type", asText(cell.get("type")));
			payload.put("receiving_label", receivingLabel(cell));
			index.put(id, payload);
		}

		var fields = node.fields();
		while (fields.hasNext()) {
			var entry = fields.next();
			walkTemplate(index, entry.getValue(), nextMajor, nextGroup, nextSection);
		}
	}

	private String receivingLabel(JsonNode node) {
		String type = asText(node.get("type"));
		if ("Course".equals(type)) {
			return courseLabel(node.get("course"));
		}
		if ("Series".equals(type)) {
			return seriesLabel(node.get("series"));
		}
		if ("Requirement".equals(type)) {
			return asText(node.path("requirement").get("name"));
		}
		return asText(node);
	}

	private String seriesLabel(JsonNode series) {
		if (series == null || series.isNull()) {
			return "";
		}
		String name = asText(series.get("name"));
		if (!name.isBlank()) {
			return name;
		}
		List<String> courses = new ArrayList<>();
		for (JsonNode course : safeArray(series.get("courses"))) {
			String label = courseLabel(course);
			if (!label.isBlank()) {
				courses.add(label);
			}
		}
		return String.join(", ", courses);
	}

	private String courseLabel(JsonNode courseContainer) {
		if (courseContainer == null || courseContainer.isNull()) {
			return "";
		}
		JsonNode course = courseContainer;
		if (courseContainer.has("course")) {
			course = courseContainer.get("course");
		}
		String prefix = asText(course.get("prefix"));
		String number = asText(course.get("courseNumber"));
		String title = asText(course.get("courseTitle"));
		String code = (prefix + " " + number).trim();
		if (!code.isBlank() && !title.isBlank()) {
			return code + " - " + title;
		}
		if (!code.isBlank()) {
			return code;
		}
		return title;
	}

	private List<String> extractSendingCourseCodes(Map<String, Object> row) {
		List<String> codes = new ArrayList<>();

		Object fulfillment = row.get("fulfillment_options");
		if (fulfillment instanceof List<?> options) {
			for (Object optionObj : options) {
				if (!(optionObj instanceof Map<?, ?> optionMap)) {
					continue;
				}
				Object coursesObj = optionMap.get("courses");
				if (coursesObj instanceof List<?> courses) {
					for (Object c : courses) {
						codes.addAll(findCourseCodes(asText(c)));
					}
				}
			}
		}

		if (codes.isEmpty()) {
			codes.addAll(findCourseCodes(asText(row.get("sending_courses"))));
		}

		return codes.stream().distinct().toList();
	}

	private List<String> findCourseCodes(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> matches = new ArrayList<>();
		Matcher matcher = COURSE_CODE_PATTERN.matcher(text.toUpperCase());
		while (matcher.find()) {
			matches.add(matcher.group().trim());
		}
		return matches;
	}

	private JsonNode parseEmbeddedJson(JsonNode node) {
		if (node == null || node.isNull()) {
			return objectMapper.nullNode();
		}
		if (node.isTextual()) {
			String text = node.asText().trim();
			if (!text.isEmpty() && (text.startsWith("{") || text.startsWith("["))) {
				try {
					return objectMapper.readTree(text);
				} catch (IOException ignored) {
					return node;
				}
			}
		}
		return node;
	}

	private Iterable<JsonNode> safeArray(JsonNode node) {
		if (node != null && node.isArray()) {
			return node;
		}
		return List.of();
	}

	private String firstName(JsonNode institutionNode) {
		JsonNode names = institutionNode.path("names");
		if (names.isArray() && names.size() > 0) {
			return asText(names.get(0).get("name"));
		}
		return asText(institutionNode.get("name"));
	}

	private String resolveInstitutionName(long id) {
		try {
			InstitutionCodeDto dto = institutionCodeService.getById(id);
			return dto.schoolName();
		} catch (Exception ignored) {
			try {
				institutionCodeService.syncFromLocalResource();
				return institutionCodeService.getById(id).schoolName();
			} catch (Exception ignoredAgain) {
				return "";
			}
		}
	}

	private static String asText(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof JsonNode node) {
			if (node.isNull()) {
				return "";
			}
			if (node.isTextual() || node.isNumber() || node.isBoolean()) {
				return node.asText().trim();
			}
			if (node.has("name") && node.get("name").isTextual()) {
				return node.get("name").asText().trim();
			}
			if (node.has("value") && node.get("value").isTextual()) {
				return node.get("value").asText().trim();
			}
			return node.toString();
		}
		return String.valueOf(value).trim();
	}

	private static String defaultIfBlank(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static Map<String, Object> ref(String type, String rowId, String receivingRequirement, String sendingCourses) {
		Map<String, Object> ref = new LinkedHashMap<>();
		ref.put("agreement_type", type);
		ref.put("row_id", rowId);
		ref.put("receiving_requirement", receivingRequirement);
		ref.put("sending_courses", sendingCourses);
		return ref;
	}

	private record SendingDetails(
			String sendingCourses,
			List<String> sendingCourseOptions,
			List<Map<String, Object>> fulfillmentOptions,
			String noArticulationReason,
			List<String> deniedCourses
	) {
	}

	private record ParsedAgreement(
			String agreementType,
			String agreementName,
			String publishDate,
			List<Map<String, Object>> rows
	) {
	}

	private record AgreementFetchResult(String status, String key, JsonNode payload) {
	}

	private static class AssistHttpClient {
		private final ObjectMapper mapper;
		private final HttpClient httpClient;
		private final Map<String, String> cookies = new LinkedHashMap<>();
		private String xsrfToken = "";

		private AssistHttpClient(ObjectMapper mapper) {
			this.mapper = mapper;
			this.httpClient = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(30))
					.build();
			bootstrapSession();
		}

		private AgreementFetchResult fetchAgreement(int year, long toId, long fromId, String prefix) {
			String canonicalKey = year + "/" + fromId + "/to/" + toId + "/" + ALL_SUFFIX_BY_PREFIX.get(prefix);
			String key = fetchAllKey(year, toId, fromId, prefix);
			if (key == null || key.isBlank()) {
				key = canonicalKey;
			}
			JsonNode payload = fetchAgreementByKey(key, true);
			if (payload == null && !Objects.equals(key, canonicalKey)) {
				payload = fetchAgreementByKey(canonicalKey, true);
				key = canonicalKey;
			}
			if (payload == null) {
				return new AgreementFetchResult("no-data", key, null);
			}
			boolean ok = payload.path("isSuccessful").asBoolean(false);
			if (!ok) {
				return new AgreementFetchResult("error", key, payload);
			}
			return new AgreementFetchResult("ok", key, payload);
		}

		private String fetchAllKey(int year, long toId, long fromId, String prefix) {
			Map<String, String> params = new LinkedHashMap<>();
			params.put("receivingInstitutionId", String.valueOf(toId));
			params.put("sendingInstitutionId", String.valueOf(fromId));
			params.put("academicYearId", String.valueOf(year));
			params.put("categoryCode", CATEGORY_BY_PREFIX.get(prefix));
			JsonNode data = getJson(BASE_URL + "/api/agreements", params, true, 5);
			if (data == null) {
				return null;
			}
			JsonNode allReports = data.path("allReports");
			if (allReports.isArray() && allReports.size() > 0) {
				return allReports.get(0).path("key").asText("");
			}
			return null;
		}

		private JsonNode fetchAgreementByKey(String key, boolean allow400) {
			Map<String, String> params = Map.of("Key", key);
			return getJson(BASE_URL + "/api/articulation/Agreements", params, allow400, 5);
		}

		private void bootstrapSession() {
			HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/"))
					.GET()
					.timeout(Duration.ofSeconds(30))
					.build();
			try {
				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				for (String rawCookie : response.headers().allValues("set-cookie")) {
					String nameValue = rawCookie.split(";", 2)[0];
					int idx = nameValue.indexOf('=');
					if (idx <= 0) {
						continue;
					}
					String name = nameValue.substring(0, idx);
					String value = nameValue.substring(idx + 1);
					cookies.put(name, value);
				}
				xsrfToken = cookies.getOrDefault("X-XSRF-TOKEN", cookies.getOrDefault("XSRF-TOKEN", ""));
				xsrfToken = URLDecoder.decode(xsrfToken, StandardCharsets.UTF_8);
			} catch (Exception e) {
				throw new IllegalArgumentException("Failed to initialize ASSIST session: " + e.getMessage(), e);
			}
		}

		private JsonNode getJson(String baseUrl, Map<String, String> params, boolean allow400, int retries) {
			double backoffSeconds = 1.0;
			Exception last = null;
			for (int attempt = 0; attempt < retries; attempt++) {
				try {
					URI uri = URI.create(baseUrl + "?" + buildQuery(params));
					HttpRequest request = HttpRequest.newBuilder(uri)
							.header("Accept", "application/json, text/plain, */*")
							.header("Referer", BASE_URL + "/")
							.header("Origin", BASE_URL)
							.header("X-XSRF-TOKEN", xsrfToken)
							.GET()
							.timeout(Duration.ofSeconds(120))
							.build();

					if (!cookies.isEmpty()) {
						String cookieHeader = cookies.entrySet().stream()
								.map(entry -> entry.getKey() + "=" + entry.getValue())
								.reduce((a, b) -> a + "; " + b)
								.orElse("");
						request = HttpRequest.newBuilder(uri)
								.header("Accept", "application/json, text/plain, */*")
								.header("Referer", BASE_URL + "/")
								.header("Origin", BASE_URL)
								.header("X-XSRF-TOKEN", xsrfToken)
								.header("Cookie", cookieHeader)
								.GET()
								.timeout(Duration.ofSeconds(120))
								.build();
					}
					HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					int status = response.statusCode();

					if (status == 429) {
						sleep(backoffSeconds);
						backoffSeconds = Math.min(backoffSeconds * 2, 30.0);
						continue;
					}
					if (allow400 && status == 400) {
						return null;
					}
					if (status >= 200 && status < 300) {
						return mapper.readTree(response.body());
					}
					last = new IllegalStateException("HTTP " + status + " from " + uri);
					sleep(backoffSeconds);
					backoffSeconds = Math.min(backoffSeconds * 2, 30.0);
				} catch (Exception ex) {
					last = ex;
					sleep(backoffSeconds);
					backoffSeconds = Math.min(backoffSeconds * 2, 30.0);
				}
			}
			throw new IllegalArgumentException("ASSIST request failed: " + (last == null ? "unknown" : last.getMessage()), last);
		}

		private static String buildQuery(Map<String, String> params) {
			List<String> parts = new ArrayList<>();
			for (Map.Entry<String, String> entry : params.entrySet()) {
				parts.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
						+ "="
						+ URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
			}
			return String.join("&", parts);
		}

		private static void sleep(double seconds) {
			try {
				Thread.sleep((long) (seconds * 1000));
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
