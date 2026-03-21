create table if not exists users (
  id varchar(64) primary key,
  username varchar(64) not null unique,
  password_hash varchar(120) not null,
  created_at timestamp not null
);

create table if not exists auth_sessions (
  token varchar(128) primary key,
  user_id varchar(64) not null,
  expires_at timestamp not null,
  created_at timestamp not null
);

create table if not exists games (
  id varchar(64) primary key,
  room_id varchar(64) not null,
  game_type varchar(16) not null,
  is_training boolean not null default false,
  opponent_type varchar(32) not null default 'HUMAN',
  ai_engine varchar(64),
  difficulty varchar(16),
  status varchar(16) not null,
  first_user_id varchar(64) not null,
  first_username varchar(64) not null,
  first_side varchar(16) not null,
  second_user_id varchar(64) not null,
  second_username varchar(64) not null,
  second_side varchar(16) not null,
  current_turn varchar(16),
  winner_side varchar(16),
  result_text varchar(255),
  board_json text not null,
  move_count int not null default 0,
  initial_time_seconds int not null default 0,
  first_remaining_seconds int not null default 0,
  second_remaining_seconds int not null default 0,
  termination_reason varchar(64),
  created_at timestamp not null,
  started_at timestamp not null,
  finished_at timestamp
);

create table if not exists game_moves (
  id varchar(96) primary key,
  game_id varchar(64) not null,
  move_index int not null,
  actor_user_id varchar(64) not null,
  side varchar(16) not null,
  notation varchar(120),
  payload_json text not null,
  created_at timestamp not null
);

alter table games add column if not exists initial_time_seconds int not null default 0;
alter table games add column if not exists first_remaining_seconds int not null default 0;
alter table games add column if not exists second_remaining_seconds int not null default 0;
alter table games add column if not exists termination_reason varchar(64);
alter table games add column if not exists is_training boolean not null default false;
alter table games add column if not exists opponent_type varchar(32) not null default 'HUMAN';
alter table games add column if not exists ai_engine varchar(64);
alter table games add column if not exists difficulty varchar(16);
