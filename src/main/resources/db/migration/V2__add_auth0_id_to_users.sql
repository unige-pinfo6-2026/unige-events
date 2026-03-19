alter table users add column if not exists auth0_id varchar(255);

update users
set auth0_id = email
where auth0_id is null;

alter table users alter column auth0_id set not null;
alter table users alter column is_profile_public set default false;
alter table users alter column is_admin set default false;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'users_auth0_id_unique'
    ) then
        alter table users add constraint users_auth0_id_unique unique (auth0_id);
    end if;
end
$$;
