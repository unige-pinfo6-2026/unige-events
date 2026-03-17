create sequence event_seq start with 1 increment by 50;

create table Event (
    id          bigint      not null default nextval('event_seq'),
    title       varchar(255) not null,
    description varchar(255),
    primary key (id)
);

create table users (
    id                uuid         not null,
    email             varchar(255) not null,
    avatar_url        varchar(255),
    bio               TEXT,
    created_at        timestamp(6) not null,
    display_name      varchar(255),
    faculty           varchar(255),
    interests         TEXT,
    is_admin          boolean      not null,
    is_profile_public boolean      not null,
    study_level       varchar(255),
    primary key (id)
);

alter table users add constraint users_email_unique unique (email);