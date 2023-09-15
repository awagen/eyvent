package de.awagen.eyvent.config

object EnvVariableKeys extends Enumeration {
  type EnvVariableKeys = Val

  protected case class Val(key: String, default: String) extends super.Val {
    lazy val value: String = sys.env.getOrElse(key, default)
  }

  val PROFILE: Val = Val("PROFILE", "")
}
