CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    guest BOOLEAN NOT NULL DEFAULT FALSE,
    roles VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    display_name VARCHAR(120) NOT NULL,
    avatar_url VARCHAR(512),
    locale VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE statistics (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    games_played INTEGER NOT NULL DEFAULT 0,
    wins INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0,
    play_time_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    room_code VARCHAR(16) NOT NULL UNIQUE,
    host_user_id UUID NOT NULL,
    game_type VARCHAR(30) NOT NULL,
    room_type VARCHAR(20) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    state VARCHAR(30) NOT NULL,
    max_players INTEGER NOT NULL,
    current_session_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE players (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    user_id UUID NOT NULL REFERENCES users(id),
    display_name VARCHAR(120) NOT NULL,
    connected BOOLEAN NOT NULL DEFAULT TRUE,
    ai_controlled BOOLEAN NOT NULL DEFAULT FALSE,
    ai_type VARCHAR(30),
    ai_difficulty VARCHAR(20),
    seat_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_room_user UNIQUE (room_id, user_id)
);

CREATE TABLE game_sessions (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    game_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    state_payload TEXT NOT NULL,
    save_version BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE game_events (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES game_sessions(id),
    room_id UUID NOT NULL REFERENCES rooms(id),
    event_type VARCHAR(60) NOT NULL,
    actor_user_id UUID,
    payload TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    sender_user_id UUID,
    target_user_id UUID,
    sender_name VARCHAR(120) NOT NULL,
    content VARCHAR(500) NOT NULL,
    system_message BOOLEAN NOT NULL DEFAULT FALSE,
    ai_message BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE monopoly_games (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES game_sessions(id),
    phase VARCHAR(40) NOT NULL,
    current_player_id UUID,
    turn_counter INTEGER NOT NULL DEFAULT 0,
    state_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE monopoly_properties (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id),
    property_name VARCHAR(120) NOT NULL,
    owner_player_id UUID,
    mortgaged BOOLEAN NOT NULL DEFAULT FALSE,
    houses INTEGER NOT NULL DEFAULT 0,
    hotel BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE mafia_games (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE REFERENCES game_sessions(id),
    phase VARCHAR(40) NOT NULL,
    day_number INTEGER NOT NULL DEFAULT 1,
    state_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE mafia_roles (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id),
    player_id UUID NOT NULL,
    role_name VARCHAR(40) NOT NULL,
    alive BOOLEAN NOT NULL DEFAULT TRUE,
    revealed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE mafia_votes (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES game_sessions(id),
    voter_player_id UUID NOT NULL,
    target_player_id UUID NOT NULL,
    cycle_number INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ai_memory (
    id UUID PRIMARY KEY,
    ai_type VARCHAR(30) NOT NULL,
    scope_id UUID NOT NULL,
    memory_key VARCHAR(120) NOT NULL,
    memory_value TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audit_entries (
    id UUID PRIMARY KEY,
    room_id UUID,
    session_id UUID,
    actor_user_id UUID,
    audit_type VARCHAR(60) NOT NULL,
    message VARCHAR(255) NOT NULL,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_rooms_host_user_id ON rooms(host_user_id);
CREATE INDEX idx_players_room_id ON players(room_id);
CREATE INDEX idx_game_sessions_room_id ON game_sessions(room_id);
CREATE INDEX idx_game_events_room_id ON game_events(room_id);
CREATE INDEX idx_chat_messages_room_id ON chat_messages(room_id);
CREATE INDEX idx_monopoly_properties_session_id ON monopoly_properties(session_id);
CREATE INDEX idx_mafia_roles_session_id ON mafia_roles(session_id);
CREATE INDEX idx_mafia_votes_session_id ON mafia_votes(session_id);
CREATE INDEX idx_audit_entries_room_id ON audit_entries(room_id);
