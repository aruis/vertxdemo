# vert.x , postgres , Reactive Postgres Client  

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

CREATE TABLE public.edu_score (
    id character varying DEFAULT public.uuid_generate_v4() NOT NULL,
    v_lesson character varying,
    n_score numeric,
    v_name character varying
);

ALTER TABLE ONLY public.edu_score
    ADD CONSTRAINT edu_score_pk PRIMARY KEY (id);

CREATE FUNCTION public.check_score() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
declare
  result varchar;
BEGIN
  if new.n_score < 60 then
    select row_to_json(new)::varchar into result;
    perform pg_notify('flunk', result);
  end if;
  return new;
END;
$$;

CREATE TRIGGER trigger_check_score AFTER INSERT ON public.edu_score FOR EACH ROW EXECUTE PROCEDURE public.check_score();
```

run your server,access http://127.0.0.1:8899,then

```sql
INSERT INTO "public"."edu_score" ("v_lesson", "n_score", "v_name") VALUES ('Math', 59, 'Jack');
INSERT INTO "public"."edu_score" ("v_lesson", "n_score", "v_name") VALUES ('Physics', 60, 'Jack');
INSERT INTO "public"."edu_score" ("v_lesson", "n_score", "v_name") VALUES ('Chemistry', 58, 'Jack');
```

When the score of the inserted data is less than 60, the browser will show it.
