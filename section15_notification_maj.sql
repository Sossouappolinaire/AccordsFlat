-- =====================================================================
--  15) NOTIFICATION DE NOUVELLE MISE À JOUR POUR LES AUTRES COMPTES — relançable
--      À exécuter après section10_mise_a_jour_apk.sql.
--
--      Quand l'administrateur publie une version (admin_maj_publier), une notification
--      est créée automatiquement pour chaque autre compte. Elle reste « non lue » jusqu'à
--      ce que l'appli signale que la version est installée ou que l'utilisateur la ferme.
--      Si l'administrateur retire la version, les notifications correspondantes disparaissent.
-- =====================================================================

create table if not exists public.maj_notifications (
    id             bigint generated always as identity primary key,
    utilisateur_id uuid    not null references auth.users(id) on delete cascade,
    version_code   integer not null references public.maj_apk(version_code) on delete cascade,
    cree_le        timestamptz not null default now(),
    lue_le         timestamptz,
    unique (utilisateur_id, version_code)
);
create index if not exists maj_notifications_non_lues
    on public.maj_notifications (utilisateur_id) where lue_le is null;

alter table public.maj_notifications enable row level security;
revoke all on public.maj_notifications from anon, authenticated;

-- Création automatique : une ligne par compte, sauf l'administrateur qui publie.
create or replace function public.maj_notifier_comptes()
returns trigger language plpgsql security definer set search_path = public, auth as $$
begin
    insert into public.maj_notifications (utilisateur_id, version_code)
    select u.id, new.version_code
    from auth.users u
    where u.id is distinct from auth.uid()
    on conflict (utilisateur_id, version_code) do nothing;
    return new;
end $$;

drop trigger if exists maj_apk_notifier on public.maj_apk;
create trigger maj_apk_notifier
    after insert on public.maj_apk
    for each row execute function public.maj_notifier_comptes();

-- Compte connecté : ses notifications non lues, avec les détails de la version.
create or replace function public.maj_notifications_non_lues()
returns json language plpgsql stable security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'connexion requise'; end if;
    return json_build_object('ok', true, 'notifications', coalesce((
        select json_agg(json_build_object(
            'id', n.id, 'version_code', a.version_code, 'version_name', a.version_name,
            'note', coalesce(a.note, ''), 'obligatoire', a.obligatoire,
            'cree_ms', (extract(epoch from a.cree_le) * 1000)::bigint) order by a.version_code desc)
        from public.maj_notifications n
        join public.maj_apk a on a.version_code = n.version_code
        where n.utilisateur_id = auth.uid() and n.lue_le is null), '[]'::json));
end $$;

-- Compte connecté : marque comme lues ses notifications jusqu'à cette version (incluse).
-- À appeler quand la version est installée (versionCode installé >= version notifiée).
create or replace function public.maj_notification_lue(p_version_code integer)
returns json language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'connexion requise'; end if;
    update public.maj_notifications set lue_le = now()
    where utilisateur_id = auth.uid() and version_code <= p_version_code and lue_le is null;
    return json_build_object('ok', true);
end $$;

revoke all on function public.maj_notifier_comptes(), public.maj_notifications_non_lues(),
    public.maj_notification_lue(integer) from public, anon, authenticated;
grant execute on function public.maj_notifications_non_lues(), public.maj_notification_lue(integer) to authenticated;
