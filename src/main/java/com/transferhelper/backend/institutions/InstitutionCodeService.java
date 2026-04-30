package com.transferhelper.backend.institutions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class InstitutionCodeService {

	private static final String ASSIST_INSTITUTIONS_URL = "https://prod.assistng.org/Institutions/api";

	private final InstitutionCodeRepository repository;
	private final JdbcTemplate jdbcTemplate;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public InstitutionCodeService(InstitutionCodeRepository repository, JdbcTemplate jdbcTemplate) {
		this.repository = repository;
		this.jdbcTemplate = jdbcTemplate;
		this.restClient = RestClient.builder().build();
		this.objectMapper = new ObjectMapper();
	}

	@PostConstruct
	void initializeSchema() {
		ensureTableAndIndexes();
	}

	@Transactional
	public InstitutionSyncResponse syncFromAssist() {
		ensureTableAndIndexes();

		List<Map<String, Object>> payload = restClient.get()
				.uri(ASSIST_INSTITUTIONS_URL)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});

		if (payload == null || payload.isEmpty()) {
			return new InstitutionSyncResponse(0, 0);
		}

		List<InstitutionCodeEntity> entities = new ArrayList<>();
		for (Map<String, Object> row : payload) {
			Long id = asLong(row.get("id"));
			String name = asString(row.get("name"));
			if (id == null || !StringUtils.hasText(name)) {
				continue;
			}

			InstitutionCodeEntity entity = new InstitutionCodeEntity();
			entity.setId(id);
			entity.setSchoolName(name.trim());
			entity.setCode(String.valueOf(id));
			entity.setCategory(asInteger(row.get("category")));
			entity.setCommunityCollege(Boolean.TRUE.equals(row.get("isCommunityCollege")));
			entities.add(entity);
		}

		repository.saveAll(entities);
		return new InstitutionSyncResponse(payload.size(), entities.size());
	}

	@Transactional
	public InstitutionSyncResponse importFromSchoolCodes(List<InstitutionCodeImportRow> rows) {
		ensureTableAndIndexes();
		if (rows == null || rows.isEmpty()) {
			return new InstitutionSyncResponse(0, 0);
		}

		List<InstitutionCodeEntity> entities = rows.stream()
				.filter(Objects::nonNull)
				.filter(r -> r.code() != null && StringUtils.hasText(r.schoolName()))
				.map(r -> {
					InstitutionCodeEntity entity = new InstitutionCodeEntity();
					entity.setId(r.code());
					entity.setCode(String.valueOf(r.code()));
					entity.setSchoolName(r.schoolName().trim());
					entity.setCategory(r.category());
					entity.setCommunityCollege(Boolean.TRUE.equals(r.isCommunityCollege()));
					return entity;
				})
				.toList();

		repository.saveAll(entities);
		return new InstitutionSyncResponse(rows.size(), entities.size());
	}

	@Transactional
	public InstitutionSyncResponse syncFromLocalResource() {
		ensureTableAndIndexes();
		try {
			List<InstitutionCodeImportRow> rows = readLocalRows();
			return importFromSchoolCodes(rows);
		} catch (Exception ex) {
			throw new IllegalArgumentException("Local school code file could not be loaded: " + ex.getMessage(), ex);
		}
	}

	@Transactional(readOnly = true)
	public List<InstitutionCodeDto> search(String query, Integer category, Boolean isCommunityCollege) {
		List<InstitutionCodeEntity> entities;
		if (StringUtils.hasText(query)) {
			entities = repository.findBySchoolNameContainingIgnoreCaseOrCodeContainingIgnoreCase(query, query);
		} else {
			entities = repository.findAll();
		}

		return entities.stream()
				.filter(entity -> category == null || Objects.equals(category, entity.getCategory()))
				.filter(entity -> isCommunityCollege == null || isCommunityCollege == entity.isCommunityCollege())
				.sorted(Comparator.comparing(InstitutionCodeEntity::getSchoolName, String.CASE_INSENSITIVE_ORDER))
				.map(InstitutionCodeDto::fromEntity)
				.toList();
	}

	@Transactional
	public InstitutionCodeDto getById(Long id) {
		InstitutionCodeEntity entity = repository.findById(id)
				.orElseGet(() -> {
					syncFromAssist();
					return repository.findById(id)
							.orElseThrow(() -> new IllegalArgumentException(
									"Institution code not found: " + id + ". Try /api/institutions/sync and retry."));
				});
		return InstitutionCodeDto.fromEntity(entity);
	}

	@Transactional(readOnly = true)
	public InstitutionPairDto getPair(Long toId, Long fromId) {
		InstitutionCodeDto toInstitution = getById(toId);
		InstitutionCodeDto fromInstitution = getById(fromId);
		return new InstitutionPairDto(toId, fromId, toInstitution, fromInstitution, true);
	}

	private void ensureTableAndIndexes() {
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS institution_codes (
					id BIGINT PRIMARY KEY,
					school_name VARCHAR(255) NOT NULL,
					code VARCHAR(32) NOT NULL,
					category INTEGER,
					is_community_college BOOLEAN DEFAULT FALSE
				)
				""");
		jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_institution_codes_code ON institution_codes(code)");
		jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_institution_codes_school_name ON institution_codes(school_name)");
	}

	private static String asString(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static Integer asInteger(Object value) {
		if (value instanceof Number n) {
			return n.intValue();
		}
		if (value == null) {
			return null;
		}
		return Integer.valueOf(value.toString());
	}

	private static Long asLong(Object value) {
		if (value instanceof Number n) {
			return n.longValue();
		}
		if (value == null) {
			return null;
		}
		return Long.valueOf(value.toString());
	}

	private List<InstitutionCodeImportRow> readLocalRows() throws Exception {
		ClassPathResource enriched = new ClassPathResource("school_codes_with_college_type.json");
		if (enriched.exists()) {
			List<Map<String, Object>> list = objectMapper.readValue(enriched.getInputStream(), new TypeReference<>() {
			});
			return list.stream().map(this::toImportRow).toList();
		}

		ClassPathResource basic = new ClassPathResource("school_codes.txt");
		if (!basic.exists()) {
			throw new IllegalArgumentException("Missing resources school_codes_with_college_type.json and school_codes.txt");
		}

		List<Map<String, Object>> list = objectMapper.readValue(basic.getInputStream(), new TypeReference<>() {
		});
		List<InstitutionCodeImportRow> rows = new ArrayList<>();
		for (Map<String, Object> item : list) {
			Map<String, Object> normalized = new HashMap<>(item);
			normalized.putIfAbsent("isCommunityCollege", false);
			rows.add(toImportRow(normalized));
		}
		return rows;
	}

	private InstitutionCodeImportRow toImportRow(Map<String, Object> row) {
		return new InstitutionCodeImportRow(
				asLong(row.get("code")),
				asString(row.get("schoolName")),
				asInteger(row.get("category")),
				Boolean.TRUE.equals(row.get("isCommunityCollege"))
		);
	}
}
