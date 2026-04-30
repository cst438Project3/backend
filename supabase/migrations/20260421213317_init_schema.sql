create extension if not exists citext;
create extension if not exists pgcrypto;

create or replace function set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

create table app_user (
    user_id uuid primary key default gen_random_uuid(),
    provider text not null,
    provider_subject text not null,
    email citext not null unique,
    name text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_app_user_provider_subject unique (provider, provider_subject)
);

create trigger trg_app_user_updated_at
before update on app_user
for each row execute function set_updated_at();

create table institution (
    institution_id bigint generated always as identity primary key,
    name text not null,
    type text not null,
    location text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_institution_updated_at
before update on institution
for each row execute function set_updated_at();

create table student (
    student_id bigint generated always as identity primary key,
    user_id uuid unique references app_user(user_id) on delete cascade,
    name text not null,
    email citext not null unique,
    current_institution_id bigint references institution(institution_id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_student_updated_at
before update on student
for each row execute function set_updated_at();

create index idx_student_institution on student(current_institution_id);

create table course (
    course_id bigint generated always as identity primary key,
    institution_id bigint not null references institution(institution_id) on delete restrict,
    course_code text not null,
    title text not null,
    credits numeric(5,2) not null check (credits >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_course_institution_code unique (institution_id, course_code)
);

create trigger trg_course_updated_at
before update on course
for each row execute function set_updated_at();

create index idx_course_institution on course(institution_id);

create table student_course (
    student_course_id bigint generated always as identity primary key,
    student_id bigint not null references student(student_id) on delete cascade,
    course_id bigint not null references course(course_id) on delete restrict,
    term text,
    year int,
    attempt_no int not null default 1,
    grade text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_student_course_attempt unique (student_id, course_id, attempt_no),
    constraint chk_student_course_grade
        check (
            grade is null or grade in (
                'A','A-','B+','B','B-','C+','C','C-','D','F',
                'P','NP','W','I'
            )
        )
);

create trigger trg_student_course_updated_at
before update on student_course
for each row execute function set_updated_at();

create index idx_student_course_student on student_course(student_id);
create index idx_student_course_course on student_course(course_id);

create table program (
    program_id bigint generated always as identity primary key,
    institution_id bigint not null references institution(institution_id) on delete restrict,
    name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_program_institution_name unique (institution_id, name)
);

create trigger trg_program_updated_at
before update on program
for each row execute function set_updated_at();

create index idx_program_institution on program(institution_id);

create table program_requirement (
    requirement_id bigint generated always as identity primary key,
    program_id bigint not null references program(program_id) on delete cascade,
    requirement_name text not null,
    required_credits numeric(5,2) check (required_credits is null or required_credits >= 0),
    min_courses_required int check (min_courses_required is null or min_courses_required >= 0),
    requirement_type text not null default 'course_option',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_requirement_type
        check (requirement_type in ('course_option', 'credits', 'mandatory', 'elective_group'))
);

create trigger trg_program_requirement_updated_at
before update on program_requirement
for each row execute function set_updated_at();

create index idx_program_requirement_program on program_requirement(program_id);

create table requirement_course_option (
    requirement_option_id bigint generated always as identity primary key,
    requirement_id bigint not null references program_requirement(requirement_id) on delete cascade,
    course_id bigint not null references course(course_id) on delete restrict,
    created_at timestamptz not null default now(),
    constraint uq_requirement_course_option unique (requirement_id, course_id)
);

create index idx_req_course_option_req on requirement_course_option(requirement_id);
create index idx_req_course_option_course on requirement_course_option(course_id);

create table transfer_equivalency (
    equivalency_id bigint generated always as identity primary key,
    from_course_id bigint not null references course(course_id) on delete restrict,
    to_course_id bigint not null references course(course_id) on delete restrict,
    status text not null default 'pending',
    effective_from date,
    effective_to date,
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_transfer_equivalency unique (from_course_id, to_course_id),
    constraint chk_transfer_equivalency_status
        check (status in ('pending', 'approved', 'rejected', 'expired')),
    constraint chk_transfer_equivalency_distinct
        check (from_course_id <> to_course_id)
);

create trigger trg_transfer_equivalency_updated_at
before update on transfer_equivalency
for each row execute function set_updated_at();

create index idx_transfer_eq_from on transfer_equivalency(from_course_id);
create index idx_transfer_eq_to on transfer_equivalency(to_course_id);

create table transfer_plan (
    plan_id bigint generated always as identity primary key,
    student_id bigint not null references student(student_id) on delete cascade,
    target_program_id bigint not null references program(program_id) on delete restrict,
    plan_name text,
    status text not null default 'draft',
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_transfer_plan_status
        check (status in ('draft', 'active', 'completed', 'archived'))
);

create trigger trg_transfer_plan_updated_at
before update on transfer_plan
for each row execute function set_updated_at();

create index idx_transfer_plan_student on transfer_plan(student_id);
create index idx_transfer_plan_program on transfer_plan(target_program_id);

create unique index uq_active_transfer_plan
on transfer_plan (student_id, target_program_id)
where status = 'active';
