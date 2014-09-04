package models.marathon

import play.api.libs.json.Json

case class Task(
                  appId: String,
                  id: String,
                  host: String,
                  ports: List[Int],
                  startedAt: String,
                  stagedAt: String,
                  version: String
                  )

case class Tasks (tasks: List[Task])


object Task {
  // Json reading/writing
  implicit val taskReads = Json.reads[Task]
  implicit val taskWrites = Json.writes[Task]
}

object Tasks {
  // Json reading/writing
  implicit val tasksReads = Json.reads[Tasks]
  implicit val tasksWrites = Json.writes[Tasks]
}
