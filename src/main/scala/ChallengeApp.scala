import java.io._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext._
import org.apache.spark.ml.classification.{LogisticRegression, RandomForestClassifier, RandomForestClassificationModel}
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.sql.functions.{desc, udf}
import org.apache.spark.sql.Row
import scala.collection.mutable.ListBuffer
import scala.io.Source

object ChallengeApp {

  case class Log(userId: String, eventDate: String, eventType: String)

  // All types except Purchase and CustomerSupport
  val eventTypes = Seq("EmailOpen", "FormSubmit", "EmailClickthrough", "WebVisit", "PageView") 

  def main(args: Array[String]): Unit = {

    // Instantiate the spark context
    val conf = new SparkConf().setAppName("Interview Challenge App")
      .set("spark.driver.host", "localhost")
      .setMaster(s"local[*]")

    val sc = new SparkContext(conf)
    val sqlContext = new org.apache.spark.sql.SQLContext(sc)
    import sqlContext.implicits._
    
    try {
      // Featurize the input data
      val training = featurize(sc, sqlContext, "resources/training.tsv")
      val test = featurize(sc, sqlContext, "resources/test.tsv")

/* DEBUG
      // Featurize the training data
      val userPurchaseHistoryDF = featurize(sc, sqlContext, "resources/training.tsv")

      // Let's skip the featurization since it takes a while
      // Save it to disk
      userPurchaseHistoryDF.write.format("json").save("resources/userPurchaseHistoryNonzero.json")

      // Load from disk
      val userPurchaseHistoryDF = sqlContext.read.format("json").load("resources/userPurchaseHistoryNonzero.json")

      // Divide it into two sets
      val data = userPurchaseHistoryDF.randomSplit(Array(0.6, 0.4), seed = 11L)
      val training = data(0)
      val test = data(1)
*/

      // Train our model
      val model = train(sc, training, "RandomForest")
      //val model = train(sc, training, "LogisticRegression")

      // Predict
      val predictions = predict(model, test)

/* DEBUG
      // Evaluate its performance
      val evaluator = new BinaryClassificationEvaluator()
      val auroc = evaluator.evaluate(predictions)
      predictions.show
      println("AUROC: " + auroc)
*/

      generateOutput(predictions)

    } finally {
      sc.stop()      // Stop (shut down) the context.
    }
  }


  def featurize(sc: SparkContext, sqlContext: org.apache.spark.sql.SQLContext, inputFile: String) : org.apache.spark.sql.DataFrame = {
    import sqlContext.implicits._

    // Create an RDD of Log objects and convert it to a DataFrame
    val logsDF = sc.textFile(inputFile).map(_.split("\\s")).map(l => Log(l(0), l(1), l(2))).toDF()
    
    // Get a distinct list of userIDs
    val distinctUsersDF = logsDF.select("userId").distinct
    
    // Populate the label field
    val purchasedUsersDF = logsDF.filter($"eventType" === "Purchase").select("userId", "eventType")
    
    // Distinct list of users with their purchase status
    val distinctUsersPurchasedDF = distinctUsersDF.as("a").join(purchasedUsersDF.as("b"), $"a.userId" === $"b.userId", "left").drop($"b.userId")
    
    // A function to help us explicitly fill the label column
    val purchasedUdf = udf[String,String]( eventType =>
      eventType match {
        case "Purchase" => "purchase"
        case _ => "no_purchase"
      }
    )
    
    // Explicitly fill the label column
    val usersLabeledDF = distinctUsersPurchasedDF.select($"userId", purchasedUdf($"eventType") as "purchased")

    // Get counts of each event type by user
    val eventCountsAllDF = logsDF.groupBy("userId", "eventType").count()

    // Don't include the Purchase event since we already have a separate label column and don't want to include this in the features
    val eventCountsDF = eventCountsAllDF.filter($"eventType" !== "Purchase").filter($"eventType" !== "CustomerSupport")

    val userPurchaseHistory_unpivotedDF = usersLabeledDF.as("a").join(eventCountsDF.as("b"), $"a.userId" === $"b.userId", "left")
      // Remove the redundant join column
      .drop($"b.userId")
      // Remove any duplicate counts
      .dropDuplicates

    val userPurchaseHistoryDF = userPurchaseHistory_unpivotedDF.groupBy("userId", "purchased")
      // Specify pivot values for better performance
      .pivot("eventType", eventTypes).sum("count")
      // Fill null values after the pivot with 0.0
      .na.fill(0.0, eventTypes)

    // Remove rows with zero length feature vectors
    val userPurchaseHistoryNonzeroDF = userPurchaseHistoryDF.filter("EmailClickthrough > 0 or EmailOpen > 0 or FormSubmit > 0 or PageView > 0 or WebVisit > 0")

    userPurchaseHistoryNonzeroDF
  }

  def train(sc: SparkContext, userHistoryDF: org.apache.spark.sql.DataFrame, classifier_type: String) : PipelineModel = {
    // Convert text to an index that the classifier understands
    val labelIndexer = new StringIndexer()
      .setInputCol("purchased")
      .setOutputCol("label")

    // Consolidate all numeric fields into a single Vector that the classifier understands
    val assembler = new VectorAssembler()
      .setInputCols(eventTypes.toArray)
      .setOutputCol("features")

    var classifier : org.apache.spark.ml.PipelineStage = new LogisticRegression()
    // Tell our classifier what to do
    if (classifier_type == "RandomForest") {
      classifier = new RandomForestClassifier()
        .setLabelCol("label")
        .setFeaturesCol("features")
        .setNumTrees(10)
    } else {
      classifier = new LogisticRegression()
        .setLabelCol("label")
        .setFeaturesCol("features")
        .setMaxIter(10)
        .setRegParam(0.3)
    }

    // Specify the order of steps
    val pipeline = new Pipeline()
      .setStages(Array(labelIndexer, assembler, classifier))

    // Generate the model
    pipeline.fit(userHistoryDF)
  }

  def predict(model: PipelineModel, data: org.apache.spark.sql.DataFrame) : org.apache.spark.sql.DataFrame = {
    model.transform(data)
  }

  def generateOutput(predictions: org.apache.spark.sql.DataFrame) = {
    val file = new File("output/purchasers.txt")
    val bw = new BufferedWriter(new FileWriter(file))

    // Look at users predicted to purchase and sort by highest confidence
    val userIdPrediction = predictions.filter("prediction > 0")
      .select("userId", "probability")
      .map { case Row(userId: String, probability: Vector) =>
        userId -> probability(1)
      }.sortBy(_._2, false)
    userIdPrediction.take(1000).foreach(x => bw.write(x._1 + "\n"))
    
    bw.close()
  }
}

