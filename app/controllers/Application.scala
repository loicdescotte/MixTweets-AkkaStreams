package controllers

import play.api.mvc.{Controller, Action}
import play.api.libs.iteratee._
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import play.api.libs.json.Json
import play.api.libs.EventSource
import play.api.libs.json._
import play.api.Logger
import twitter4j._
import twitter4j.{Status => TwitterStatus}
import twitter4j.auth._
import twitter4j.conf._
import org.joda.time.DateTime
import play.api.libs.streams.Streams._
import org.reactivestreams._
import akka.actor._
import akka.stream.actor._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import akka.stream.FlowMaterializer
import akka.stream.OverflowStrategy

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.streams.Streams
import play.api.libs.streams.Streams._

case class TweetInfo(searchQuery: String, message: String, author: String){
  def toJson = Json.obj("message" -> s"${this.searchQuery} : ${this.message}", "author" -> s"${this.author}")
}

class TwitterStreamListener(config: Configuration) {

  implicit val system = ActorSystem("mixedTweets")
  implicit val materializer = ActorFlowMaterializer()

  val (actorRef, publisher) =  Source.actorRef[JsValue](1000, OverflowStrategy.fail).toMat(Sink.publisher)(Keep.both).run()
 
  def listenAndStream(searchQuery: String) = {

    Logger.info(s"#start listener for $searchQuery")
 
    val statusListener = new StatusListener() {
 
      override def onStatus(status: TwitterStatus) = {   
       Logger.debug(status.getText)
       //push elements into a publisher
       actorRef ! TweetInfo(searchQuery, status.getText, status.getUser.getName).toJson
      }
 
      override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = {}
 
      override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = {}
 
      override def onException(ex: Exception) = ex.printStackTrace()
 
      override def onScrubGeo(userId: Long, upToStatusId: Long) = {}
 
      override def onStallWarning(warning: StallWarning) = {}
 
    }

    val twitterStream = new TwitterStreamFactory(config).getInstance 
    twitterStream.addListener(statusListener)
    twitterStream.filter(new FilterQuery(0, Array(), Array(searchQuery)))    
  }
 
}


object Application extends Controller { 

  val cb = new ConfigurationBuilder()
  //TODO replace with your credentials
   cb.setDebugEnabled(true)
    .setOAuthConsumerKey("xxx")
    .setOAuthConsumerSecret("xxx")
    .setOAuthAccessToken("xxx")
    .setOAuthAccessTokenSecret("xxx")

  val config = cb.build
  
  def stream(query: String) = Action {

    val twitterStreamListener = new TwitterStreamListener(config)    

    query.split(",").foreach { query =>     
      twitterStreamListener.listenAndStream(query)
    }

    val enum : Enumerator[JsValue] = Streams.publisherToEnumerator(twitterStreamListener.publisher)

    Ok.chunked(enum through EventSource()).as("text/event-stream")  
  } 

  def liveTweets(query: List[String]) = Action {        
    Ok(views.html.index(query))
  }

  def index = Action {
    //default search  
    Redirect(routes.Application.liveTweets(List("java", "ruby")))
  }
  
}
