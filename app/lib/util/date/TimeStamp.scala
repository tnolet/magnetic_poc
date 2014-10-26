package lib.util.date

import java.util.Calendar

/**
 * simple helper for creating timestamps
 */
object TimeStamp  {

  def now : java.sql.Timestamp = new java.sql.Timestamp(Calendar.getInstance().getTime.getTime)
}
