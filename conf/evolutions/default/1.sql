# --- Created by Slick DDL
# To stop Slick DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table "CONTAINER_INSTANCE" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"vrn" VARCHAR(254) NOT NULL,"host" VARCHAR(254) DEFAULT '127.0.0.1' NOT NULL,"ports" VARCHAR(254) DEFAULT '0' NOT NULL,"weight" INTEGER DEFAULT 0 NOT NULL,"mesosId" VARCHAR(254) NOT NULL,"containerId" BIGINT NOT NULL,"created_at" TIMESTAMP NOT NULL);
create table "DOCKER_CONTAINER" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"vrn" VARCHAR(254) NOT NULL,"state" VARCHAR(254) NOT NULL,"imageRepo" VARCHAR(254) NOT NULL,"imageVersion" VARCHAR(254) NOT NULL,"masterWeight" INTEGER DEFAULT 0 NOT NULL,"instanceAmount" BIGINT NOT NULL,"serviceId" BIGINT NOT NULL,"created_at" TIMESTAMP NOT NULL);
create table "DOCKER_IMAGE" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"repo" VARCHAR(254) NOT NULL,"version" VARCHAR(254) NOT NULL,"port" INTEGER NOT NULL,"mode" VARCHAR(254) NOT NULL,"arguments" VARCHAR(254) NOT NULL);
create table "ENVIRONMENT" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"state" VARCHAR(254) NOT NULL);
create table "JOB" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"status" VARCHAR(254) NOT NULL,"priority" INTEGER NOT NULL,"payload" text NOT NULL,"queue" VARCHAR(254) NOT NULL,"created_at" TIMESTAMP NOT NULL,"updated_at" TIMESTAMP NOT NULL);
create table "JOB_EVENTS" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"status" VARCHAR(254) NOT NULL,"payload" VARCHAR(254) NOT NULL,"jobId" BIGINT NOT NULL,"timestamp" TIMESTAMP NOT NULL);
create table "SERVICES" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"port" INTEGER NOT NULL,"mode" VARCHAR(254) NOT NULL,"state" VARCHAR(254) NOT NULL,"vrn" VARCHAR(254) NOT NULL,"environmentId" BIGINT NOT NULL,"serviceTypeId" BIGINT NOT NULL);
create table "SERVICE_TYPES" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"name" VARCHAR(254) NOT NULL,"version" VARCHAR(254) NOT NULL,"mode" VARCHAR(254) NOT NULL,"basePort" INTEGER NOT NULL,"systemService" BOOLEAN NOT NULL);
create table "SLAS" ("id" BIGSERIAL NOT NULL PRIMARY KEY,"state" VARCHAR(254) NOT NULL,"metricType" VARCHAR(254) NOT NULL,"lowThreshold" BIGINT NOT NULL,"highThreshold" BIGINT NOT NULL,"backoffTime" INTEGER NOT NULL,"backoffStages" INTEGER NOT NULL,"currentStage" INTEGER NOT NULL,"wscalations" INTEGER NOT NULL,"maxEscalations" INTEGER NOT NULL,"vrn" VARCHAR(254) NOT NULL,"serviceId" BIGINT NOT NULL,"created_at" TIMESTAMP NOT NULL,"updated_at" TIMESTAMP NOT NULL);
alter table "CONTAINER_INSTANCE" add constraint "CONTAINER_FK" foreign key("containerId") references "DOCKER_CONTAINER"("id") on update NO ACTION on delete CASCADE;
alter table "DOCKER_CONTAINER" add constraint "SERVICE_FK" foreign key("serviceId") references "SERVICES"("id") on update NO ACTION on delete CASCADE;
alter table "JOB_EVENTS" add constraint "JOB_FK" foreign key("jobId") references "JOB"("id") on update NO ACTION on delete NO ACTION;
alter table "SERVICES" add constraint "ENV_FK" foreign key("environmentId") references "ENVIRONMENT"("id") on update NO ACTION on delete NO ACTION;
alter table "SERVICES" add constraint "SERVICE_TYPE_FK" foreign key("serviceTypeId") references "SERVICE_TYPES"("id") on update NO ACTION on delete NO ACTION;
alter table "SLAS" add constraint "SERVICE_SLA_FK" foreign key("serviceId") references "SERVICES"("id") on update NO ACTION on delete CASCADE;

# --- !Downs

alter table "SLAS" drop constraint "SERVICE_SLA_FK";
alter table "SERVICES" drop constraint "ENV_FK";
alter table "SERVICES" drop constraint "SERVICE_TYPE_FK";
alter table "JOB_EVENTS" drop constraint "JOB_FK";
alter table "DOCKER_CONTAINER" drop constraint "SERVICE_FK";
alter table "CONTAINER_INSTANCE" drop constraint "CONTAINER_FK";
drop table "SLAS";
drop table "SERVICE_TYPES";
drop table "SERVICES";
drop table "JOB_EVENTS";
drop table "JOB";
drop table "ENVIRONMENT";
drop table "DOCKER_IMAGE";
drop table "DOCKER_CONTAINER";
drop table "CONTAINER_INSTANCE";

