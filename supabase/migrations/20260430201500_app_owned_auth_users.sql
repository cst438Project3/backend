create extension if not exists citext;
create extension if not exists pgcrypto;

create table if not exists app_user (
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

drop trigger if exists trg_app_user_updated_at on app_user;
create trigger trg_app_user_updated_at
before update on app_user
for each row execute function set_updated_at();

do $$
declare
    existing_constraint text;
begin
    select c.conname
    into existing_constraint
    from pg_constraint c
    join pg_attribute a
        on a.attrelid = c.conrelid
        and a.attnum = any(c.conkey)
    where c.conrelid = 'student'::regclass
      and c.contype = 'f'
      and a.attname = 'user_id'
    limit 1;

    if existing_constraint is not null then
        execute format('alter table student drop constraint %I', existing_constraint);
    end if;
end;
$$;

alter table student
    add constraint fk_student_app_user
    foreign key (user_id) references app_user(user_id) on delete cascade;
