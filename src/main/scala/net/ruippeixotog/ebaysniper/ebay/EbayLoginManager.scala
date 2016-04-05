package net.ruippeixotog.ebaysniper.ebay

import scala.util.Try

import com.typesafe.config.Config
import net.ruippeixotog.ebaysniper.util.Implicits._
import net.ruippeixotog.ebaysniper.util.Logging
import net.ruippeixotog.scalascraper.browser.Browser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.ElementQuery
import net.ruippeixotog.scalascraper.util.Validated.{ VFailure, VSuccess }

class EbayLoginManager(siteConf: Config, username: String, password: String)(implicit browser: Browser)
    extends Logging {

  implicit private[this] def defaultConf = siteConf

  def login(): Boolean = {
    if (browser.cookies(loginUrl).contains("shs")) true
    else forceLogin()
  }

  def loginUrl = siteConf.getString("login-form.uri-template").resolveVars()

  def forceLogin(): Boolean = {
    // TODO add support for clearing cookies in scala-scraper browsers?
    log.debug("Getting the sign in cookie for {}", siteConf.getString("name"))

    val (formData, signInAction) = browser.get(loginUrl) >> signInFormExtractor
    val signInData = formData + ("userid" -> username) + ("pass" -> password)

    browser.post(signInAction, signInData) errorIf loginErrors match {
      case VFailure(status) =>
        log.error("A problem occurred while signing in ({})", status)
        false

      case VSuccess(doc) =>
        doc errorIf loginWarnings match {
          case VFailure(status) =>
            log.warn("A warning occurred while signing in ({})", status)
          case _ =>
        }
        log.info("Login successful")
        true
    }
  }

  // TODO fix in scala-scraper
  private[this] lazy val customFormDataAndAction = { elems: ElementQuery =>
    val form = elems.head
    val action = form.attr("action")
    val kvs = form.select("input").map { e =>
      e.attr("name") -> Try(e.attr("value")).getOrElse("")
    }
    (kvs.toMap, action)
  }

  private[this] lazy val signInFormExtractor =
    customFormDataAndAction(siteConf.getString("login-form.form-query"))

  private[this] lazy val loginErrors = validatorsAt[String](siteConf, "login-confirm.error-statuses")
  private[this] lazy val loginWarnings = validatorsAt[String](siteConf, "login-confirm.warn-statuses")
}
