-- =====================================================================
-- 16) FOOTBALL : invitations et matchs 1 contre 1 en ligne
-- À exécuter une fois dans Supabase > SQL Editor après la configuration
-- principale et section11_motard_combat.sql.
--
-- Les joueurs utilisent l'authentification existante. Le client hôte
-- simule le match ; les actions de l'invité et l'état du match transitent
-- par ces fonctions toutes les ~165 ms.
-- =====================================================================

create table if not exists public.duels_foot (
    id              uuid primary key default gen_random_uuid(),
    hote            uuid not null references auth.users (id) on delete cascade,
    invite          uuid not null references auth.users (id) on delete cascade,
    hote_nom        text,
    invite_nom      text,
    mode            text not null check (mode in ('foot', 'pes')),
    etat            text not null default 'invitation'
                    check (etat in ('invitation', 'accepte', 'en_cours', 'refuse', 'annule', 'termine')),
    entree_hote     jsonb not null default '{}'::jsonb,
    entree_invite   jsonb not null default '{}'::jsonb,
    instantane      jsonb not null default '{}'::jsonb,
    hote_vu         timestamptz not null default now(),
    invite_vu       timestamptz not null default now(),
    cree            timestamptz not null default now(),
    debut           timestamptz,
    constraint duel_foot_pas_soi_meme check (hote <> invite)
);

create index if not exists duels_foot_invite_etat
    on public.duels_foot (invite, etat, cree desc);

alter table public.duels_foot enable row level security;
drop policy if exists "duel foot lecture" on public.duels_foot;
create policy "duel foot lecture" on public.duels_foot
    for select to authenticated using (auth.uid() in (hote, invite));

revoke all on public.duels_foot from anon, authenticated;
grant select on public.duels_foot to authenticated;

create or replace function public.foot_inviter(p_invite uuid, p_mode text) returns json
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_hote_nom text;
    v_invite_nom text;
    v_id uuid;
begin
    if v_uid is null then raise exception 'connexion requise'; end if;
    if p_invite is null or p_invite = v_uid then raise exception 'joueur invalide'; end if;
    if p_mode is null or p_mode not in ('foot', 'pes') then
        raise exception 'mode de jeu invalide';
    end if;

    select coalesce(nullif(btrim(nom), ''), 'Joueur')
      into v_hote_nom from public.profils where id = v_uid;
    select coalesce(nullif(btrim(nom), ''), 'Joueur')
      into v_invite_nom from public.profils
     where id = p_invite and derniere_activite > now() - interval '3 minutes';
    if v_invite_nom is null then raise exception 'ce joueur n''est plus en ligne'; end if;

    update public.duels_foot set etat = 'annule'
     where hote = v_uid and etat in ('invitation', 'accepte');

    insert into public.duels_foot (hote, invite, hote_nom, invite_nom, mode)
    values (v_uid, p_invite, v_hote_nom, v_invite_nom, p_mode)
    returning id into v_id;

    return json_build_object('id', v_id);
end $$;

create or replace function public.foot_invitations() returns json
language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'connexion requise'; end if;
    return coalesce((
        select json_agg(json_build_object('id', id, 'de', hote_nom, 'mode', mode) order by cree desc)
          from public.duels_foot
         where invite = auth.uid() and etat = 'invitation'
           and cree > now() - interval '2 minutes'
    ), '[]'::json);
end $$;

create or replace function public.foot_repondre(p_duel uuid, p_oui boolean) returns json
language plpgsql security definer set search_path = public as $$
declare v_etat text;
begin
    if auth.uid() is null then raise exception 'connexion requise'; end if;
    update public.duels_foot
       set etat = case when p_oui then 'accepte' else 'refuse' end,
           debut = case when p_oui then now() else debut end,
           invite_vu = now()
     where id = p_duel and invite = auth.uid() and etat = 'invitation'
       and cree > now() - interval '2 minutes'
     returning etat into v_etat;
    return json_build_object('etat', coalesce(v_etat, 'expire'));
end $$;

create or replace function public.foot_info(p_duel uuid) returns json
language plpgsql security definer set search_path = public as $$
declare d public.duels_foot; v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'connexion requise'; end if;
    select * into d from public.duels_foot
     where id = p_duel and v_uid in (hote, invite) for update;
    if not found then return json_build_object('etat', 'introuvable'); end if;
    if d.etat = 'invitation' and d.cree < now() - interval '2 minutes' then
        update public.duels_foot set etat = 'annule' where id = p_duel;
        d.etat := 'annule';
    end if;
    return json_build_object('id', d.id, 'etat', d.etat, 'mode', d.mode,
        'hote_nom', d.hote_nom, 'invite_nom', d.invite_nom);
end $$;

create or replace function public.foot_sync(
    p_duel uuid,
    p_input jsonb,
    p_snapshot jsonb default null
) returns json
language plpgsql security definer set search_path = public as $$
declare
    d public.duels_foot;
    v_uid uuid := auth.uid();
    v_input jsonb;
    v_jx numeric;
    v_jy numeric;
    v_action integer;
    v_seq bigint;
begin
    if v_uid is null then raise exception 'connexion requise'; end if;
    if p_input is not null and octet_length(p_input::text) > 2048 then
        raise exception 'entrée trop volumineuse';
    end if;
    if p_snapshot is not null and octet_length(p_snapshot::text) > 65536 then
        raise exception 'état du match trop volumineux';
    end if;

    select * into d from public.duels_foot
     where id = p_duel and v_uid in (hote, invite) for update;
    if not found then return json_build_object('ok', false, 'etat', 'introuvable'); end if;
    if d.etat not in ('accepte', 'en_cours') then
        return json_build_object('ok', false, 'etat', d.etat);
    end if;
    if p_snapshot is not null and v_uid <> d.hote then
        raise exception 'seul l''hôte peut publier l''état du match';
    end if;
    if p_snapshot is not null and jsonb_typeof(p_snapshot) <> 'object' then
        raise exception 'état du match invalide';
    end if;

    begin
        v_jx := greatest(-1, least(1, coalesce((p_input->>'jx')::numeric, 0)));
        v_jy := greatest(-1, least(1, coalesce((p_input->>'jy')::numeric, 0)));
        v_action := greatest(0, least(4, coalesce((p_input->>'action')::integer, 0)));
        v_seq := greatest(0, coalesce((p_input->>'seq')::bigint, 0));
    exception when others then
        raise exception 'commandes de jeu invalides';
    end;
    v_input := jsonb_build_object(
        'jx', v_jx, 'jy', v_jy,
        'sprint', coalesce((p_input->>'sprint')::boolean, false),
        'action', v_action, 'seq', v_seq
    );

    if v_uid = d.hote then
        update public.duels_foot
           set entree_hote = v_input,
               instantane = coalesce(p_snapshot, instantane),
               hote_vu = now(),
               etat = 'en_cours',
               debut = coalesce(debut, now())
         where id = p_duel;
    else
        update public.duels_foot
           set entree_invite = v_input, invite_vu = now(), etat = 'en_cours',
               debut = coalesce(debut, now())
         where id = p_duel;
    end if;

    select * into d from public.duels_foot where id = p_duel;
    if d.etat = 'en_cours'
       and (d.hote_vu < now() - interval '45 seconds'
            or d.invite_vu < now() - interval '45 seconds') then
        update public.duels_foot set etat = 'termine' where id = p_duel;
        d.etat := 'termine';
    end if;

    return json_build_object(
        'ok', true,
        'etat', d.etat,
        'snapshot', d.instantane,
        'adversaire', case when v_uid = d.hote then d.entree_invite else d.entree_hote end
    );
end $$;

create or replace function public.foot_quitter(p_duel uuid) returns json
language plpgsql security definer set search_path = public as $$
declare v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'connexion requise'; end if;
    update public.duels_foot set etat = 'termine'
     where id = p_duel and v_uid in (hote, invite)
       and etat in ('invitation', 'accepte', 'en_cours');
    return json_build_object('ok', true);
end $$;

revoke all on function public.foot_inviter(uuid, text) from public, anon, authenticated;
revoke all on function public.foot_invitations() from public, anon, authenticated;
revoke all on function public.foot_repondre(uuid, boolean) from public, anon, authenticated;
revoke all on function public.foot_info(uuid) from public, anon, authenticated;
revoke all on function public.foot_sync(uuid, jsonb, jsonb) from public, anon, authenticated;
revoke all on function public.foot_quitter(uuid) from public, anon, authenticated;

grant execute on function public.foot_inviter(uuid, text) to authenticated;
grant execute on function public.foot_invitations() to authenticated;
grant execute on function public.foot_repondre(uuid, boolean) to authenticated;
grant execute on function public.foot_info(uuid) to authenticated;
grant execute on function public.foot_sync(uuid, jsonb, jsonb) to authenticated;
grant execute on function public.foot_quitter(uuid) to authenticated;
