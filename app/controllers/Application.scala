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

case class TweetInfo(searchQuery: String, message: String, author: String)

class TwitterStreamListener(searchQuery: String, config: Configuration) {

  implicit val system = ActorSystem("mixedTweets")
  implicit val materializer = ActorFlowMaterializer()

  val query = new FilterQuery(0, Array(), Array(searchQuery))
 
  val twitterStream = new TwitterStreamFactory(config).getInstance
 
  def listenAndStream = {

    val (actorRef, publisher) =  Source.actorRef[TweetInfo](1000, OverflowStrategy.fail).toMat(Sink.publisher)(Keep.both).run()

    Logger.info(s"#start listener for $searchQuery")
 
    val statusListener = new StatusListener() {
 
      override def onStatus(status: TwitterStatus) = {   
       Logger.debug(status.getText)
       //push elements into a publisher
       actorRef ! TweetInfo(searchQuery, status.getText, status.getUser.getName)
      }
 
      override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = {}
 
      override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = {}
 
      override def onException(ex: Exception) = ex.printStackTrace()
 
      override def onScrubGeo(userId: Long, upToStatusId: Long) = {}
 
      override def onStallWarning(warning: StallWarning) = {}
 
    }
 
    twitterStream.addListener(statusListener)
    twitterStream.filter(query)
    
    Source(publisher)
  }
 
}


object Application extends Controller { 

  def sourceToEnumerator[Out, Mat](source: Source[Out, Mat])(implicit fm: FlowMaterializer): Enumerator[Out] = {
    val pubr: Publisher[Out] = source.runWith(Sink.publisher[Out])
    Streams.publisherToEnumerator(pubr)
  }

  val cb = new ConfigurationBuilder()
  //TODO replace with your credentials
  cb.setDebugEnabled(true)
  .setOAuthConsumerKey("xxx")
  .setOAuthConsumerSecret("xxx")
  .setOAuthAccessToken("xxx")
  .setOAuthAccessTokenSecret("xxx")

  val config = cb.build
  
  def stream(query: String) = Action {

    implicit val system = ActorSystem("mixedTweets")
    implicit val materializer = ActorFlowMaterializer()

    val toJson = (tweet: TweetInfo) => Json.obj("message" -> s"${tweet.searchQuery} : ${tweet.message}", "author" -> s"${tweet.author}")

    val queries = query.split(",")

    val streams = queries.map { query => 
      val twitterStreamListener = new TwitterStreamListener(query, config)
      twitterStreamListener.listenAndStream 
    }

    val mergedStream = Source[TweetInfo]() { implicit builder =>

      val merge = builder.add(Merge[TweetInfo](streams.length))

      for (i <- 0 until streams.length) {
        builder.addEdge(builder.add(streams(i)), merge.in(i))
      }

      merge.out
    }

    //use this when avalaible
    //val mergedStream = streams.flatten(FlattenStrategy.concat)

    val jsonStream = mergedStream.map(tweets => toJson(tweets))

    val jsonEumerator : Enumerator[JsValue] = sourceToEnumerator(jsonStream)

    Ok.chunked(jsonEumerator through EventSource()).as("text/event-stream")  
  } 

  def liveTweets(query: List[String]) = Action {        
    Ok(views.html.index(query))
  }

  def index = Action {
    //default search  
    Redirect(routes.Application.liveTweets(List("java", "ruby")))
  }
  
}