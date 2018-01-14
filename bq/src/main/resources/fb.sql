create or replace function public.update_changetimestamp_column()
  returns trigger as $$
begin
  NEW.time_last_update = now();
  return NEW;
end;
$$ language 'plpgsql';

create schema fb;
set search_path = fb;

create table fetchtime (
  seqno            serial                                 not null,
  "table"          text primary key                       not null,
  time_last_fetch  timestamptz,
  time_insert      timestamptz default now()              not null,
  time_last_update timestamptz
);

create trigger fetchtime_changetimestamp
before update
  on fetchtime
for each row execute procedure
  public.update_changetimestamp_column();