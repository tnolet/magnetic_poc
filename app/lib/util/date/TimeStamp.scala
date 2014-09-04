package lib.util.date

import java.util.Calendar

/**
 * Created by tim on 03/09/14.
 */
object TimeStamp {

  def now = new java.sql.Timestamp(Calendar.getInstance().getTime().getTime())


}
