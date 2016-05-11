Interview Challenge
========

To get started, download and install Scala 2.10, sbt, and Spark 1.6.1.

## Download and install Spark

Download the prebuilt version 1.6.1 from here: [Download Spark](http://spark.apache.org/downloads.html)  
Move it to the standard installation directory on your machine.  
Set the `$SPARK_HOME` environment variable to this directory.

# Compilation

To build from source, execute the package command from sbt:

```
challenge.git$ sbt package
```

# Input files

Copy the training.tsv and test.tsv files into the `resources` directory. The expected path is:

```
resources/training.tsv  
resources/test.tsv
```

# Execution

To generate output files, run the jar you just created in standalone mode. This will run locally on a single machine.

```
challenge.git$ $SPARK_HOME/spark-submit target/scala-2.10/interview-challenge_2.10-1.0.jar
```

# Results

## Activity Types

The activity types most useful in predicting which user will convert in the future are:

```
EmailOpen  
FormSubmit  
EmailClickthrough  
WebVisit  
PageView
```

The counts for these activity types were used as features in the statistical model.

## Conversion

The relevant output file can be found here:

```
output/purchasers.txt  
```

This file contains a list of the userIds most likely to convert, sorted from most likely to least likely.  

# Scalability

While this sample code runs on a single node, the driver could easily be modified to operate on a full Spark cluster, whether standalone or Hadoop-based. 

# Future considerations

This represents an initial attempt to predict whether users will make a purchase based on their behavior. The following approaches are also worth considering:

* Incorporating sequence rather than simple counts of activity types.
* Incorporating days between events. This data set could be formed into a time-series structure, which would preserve the time dimension included in the logs, not just the sequence of events.
* Different types of classifiers produce different results. For this example, logistic regression and random forest classifiers were compared. Random forest provided slightly better accuracy on a random sampling of the training set, but performance of the two is very similar. It might be worthwhile to investigate the accuracies of additional types of binary classifiers. MLlib's pipeline structure makes this very easy.

