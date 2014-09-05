package lib.util.vamp

/**
 * Created by tim on 05/09/14.
 */
object Number {
    def rnd = java.util.UUID.randomUUID().toString.replace("-","").take(8)
}
