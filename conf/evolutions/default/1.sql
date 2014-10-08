# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table "CONTAINER_INSTANCE" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"vrn" VARCHAR NOT NULL,"host" VARCHAR DEFAULT '127.0.0.1' NOT NULL,"ports" VARCHAR DEFAULT '0' NOT NULL,"weight" INTEGER DEFAULT 0 NOT NULL,"mesosId" VARCHAR NOT NULL,"containerId" BIGINT NOT NULL,"created_at" TIMESTAMP NOT NULL);
create table "DOCKER_CONTAINER" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"vrn" VARCHAR NOT NULL,"status" VARCHAR NOT NULL,"imageRepo" VARCHAR NOT NULL,"imageVersion" VARCHAR NOT NULL,"masterWeight" INTEGER DEFAULT 0 NOT NULL,"instanceAmount" BIGINT NOT NULL,"serviceId" BIGINT NOT NULL,"created_at" TIMESTAMP NOT NULL);
create table "DOCKER_IMAGE" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"name" VARCHAR NOT NULL,"repo" VARCHAR NOT NULL,"version" VARCHAR NOT NULL,"port" INTEGER NOT NULL,"mode" VARCHAR NOT NULL,"arguments" VARCHAR NOT NULL);
create table "ENVIRONMENT" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"name" VARCHAR NOT NULL,"state" VARCHAR NOT NULL);
create table "JOB" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"status" VARCHAR NOT NULL,"priority" INTEGER NOT NULL,"payload" VARCHAR NOT NULL,"queue" VARCHAR NOT NULL,"created_at" TIMESTAMP NOT NULL,"updated_at" TIMESTAMP NOT NULL);
create table "JOB_EVENTS" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"status" VARCHAR NOT NULL,"payload" VARCHAR NOT NULL,"jobId" BIGINT NOT NULL,"timestamp" TIMESTAMP NOT NULL);
create table "SERVICES" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"port" INTEGER NOT NULL,"mode" VARCHAR NOT NULL,"state" VARCHAR NOT NULL,"vrn" VARCHAR NOT NULL,"environmentId" BIGINT NOT NULL,"serviceTypeId" BIGINT NOT NULL);
create table "SERVICE_TYPES" ("id" BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH 1) NOT NULL PRIMARY KEY,"name" VARCHAR NOT NULL,"version" VARCHAR NOT NULL,"mode" VARCHAR NOT NULL,"basePort" INTEGER NOT NULL);
alter table "CONTAINER_INSTANCE" add constraint "CONTAINER_FK" foreign key("containerId") references "DOCKER_CONTAINER"("id") on update NO ACTION on delete CASCADE;
alter table "DOCKER_CONTAINER" add constraint "SERVICE_FK" foreign key("serviceId") references "SERVICES"("id") on update NO ACTION on delete CASCADE;
alter table "JOB_EVENTS" add constraint "JOB_FK" foreign key("jobId") references "JOB"("id") on update NO ACTION on delete NO ACTION;
alter table "SERVICES" add constraint "ENV_FK" foreign key("environmentId") references "ENVIRONMENT"("id") on update NO ACTION on delete NO ACTION;
alter table "SERVICES" add constraint "SERVICE_TYPE_FK" foreign key("serviceTypeId") references "SERVICE_TYPES"("id") on update NO ACTION on delete NO ACTION;

# --- !Downs

alter table "SERVICES" drop constraint "ENV_FK";
alter table "SERVICES" drop constraint "SERVICE_TYPE_FK";
alter table "JOB_EVENTS" drop constraint "JOB_FK";
alter table "DOCKER_CONTAINER" drop constraint "SERVICE_FK";
alter table "CONTAINER_INSTANCE" drop constraint "CONTAINER_FK";
drop table "SERVICE_TYPES";
drop table "SERVICES";
drop table "JOB_EVENTS";
drop table "JOB";
drop table "ENVIRONMENT";
drop table "DOCKER_IMAGE";
drop table "DOCKER_CONTAINER";
drop table "CONTAINER_INSTANCE";

