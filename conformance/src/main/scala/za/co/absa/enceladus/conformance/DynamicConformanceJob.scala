/*
 * Copyright 2018-2019 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.enceladus.conformance

import java.io.{PrintWriter, StringWriter}
import java.text.MessageFormat

import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}
import org.apache.spark.sql
import org.apache.spark.sql.functions.{lit, to_date}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import za.co.absa.atum.AtumImplicits
import za.co.absa.atum.AtumImplicits.{DataSetWrapper, StringToPath}
import za.co.absa.atum.core.Atum
import za.co.absa.enceladus.conformance.datasource.DataSource
import za.co.absa.enceladus.conformance.interpreter.{DynamicInterpreter, FeatureSwitches}
import za.co.absa.enceladus.conformance.interpreter.rules.ValidationException
import za.co.absa.enceladus.dao.menasplugin.MenasPlugin
import za.co.absa.enceladus.dao.{EnceladusDAO, EnceladusRestDAO}
import za.co.absa.enceladus.model.Dataset
import za.co.absa.enceladus.utils.fs.FileSystemVersionUtils
import za.co.absa.enceladus.utils.performance.{PerformanceMeasurer, PerformanceMetricTools}
import za.co.absa.enceladus.utils.time.TimeZoneNormalizer

import scala.util.control.NonFatal

object DynamicConformanceJob {
  TimeZoneNormalizer.normalizeJVMTimeZone()

  private val infoDateColumn = "enceladus_info_date"
  private val infoDateColumnString = s"${infoDateColumn}_string"
  private val reportDateFormat = "yyyy-MM-dd"
  private val infoVersionColumn = "enceladus_info_version"

  private val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val conf: Config = ConfigFactory.load()

  def main(args: Array[String]) {
    implicit val spark: SparkSession = obtainSparkSession() // initialize spark
    implicit val cmd: CmdConfig = CmdConfig.getCmdLineArguments(args)
    implicit val fsUtils: FileSystemVersionUtils = new FileSystemVersionUtils(spark.sparkContext.hadoopConfiguration)
    implicit val dao: EnceladusDAO = EnceladusRestDAO // use REST DAO

    val enableCF: Boolean = true

    EnceladusRestDAO.login = cmd.menasCredentials
    EnceladusRestDAO.enceladusLogin()

    // get the dataset definition
    val conformance = dao.getDataset(cmd.datasetName, cmd.datasetVersion)
    val dateTokens = cmd.reportDate.split("-")

    val reportVersion = cmd.reportVersion match {
      case Some(version) => version
      case None => inferVersion(conformance.hdfsPublishPath, cmd.reportDate)
    }

    val pathCfg = PathCfg(
      publishPath = buildPublishPath(infoDateColumn, infoVersionColumn, cmd, conformance, reportVersion),
      stdPath = MessageFormat.format(conf.getString("standardized.hdfs.path"), cmd.datasetName,
        cmd.datasetVersion.toString, cmd.reportDate, reportVersion.toString)
    )
    log.info(s"stdpath = ${pathCfg.stdPath}, publishPath = ${pathCfg.publishPath}")
    // die before performing any computation if the output path already exists
    if (fsUtils.hdfsExists(pathCfg.publishPath)) {
      throw new IllegalStateException(
        s"Path ${pathCfg.publishPath} already exists. Increment the run version, or delete ${pathCfg.publishPath}")
    }

    initFunctionalExtensions()
    val performance = initPerformanceMeasurer(pathCfg.stdPath)

    // load data for input and mapping tables
    val inputData = DataSource.getData(pathCfg.stdPath, dateTokens(0), dateTokens(1), dateTokens(2), "")

    val result = conform(conformance, inputData, enableCF)

    processResult(result, performance, pathCfg, reportVersion, args.mkString(" "))
  }

  private def isExperimentalRuleEnabled()(implicit cmd: CmdConfig): Boolean = {
    val enabled = getCmdOrConfigBoolean(cmd.experimentalMappingRule,
      "conformance.mapping.rule.experimental.implementation",
      defaultValue = false)
    log.info(s"Experimental mapping rule enabled = $enabled")
    enabled
  }

  private def isCatalystWorkaroundEnabled()(implicit cmd: CmdConfig): Boolean = {
    val enabled = getCmdOrConfigBoolean(cmd.isCatalystWorkaroundEnabled,
      "conformance.catalyst.workaround",
      defaultValue = true)
    log.info(s"Catalyst workaround enabled = $enabled")
    enabled
  }

  /**
    * Returns an effective value of a parameter according to the following priorities:
    * - Command line arguments [highest]
    * - Configuration file (application.conf)
    * - Global default [lowest]
    *
    * @param cmdParameterOpt An optional value retrieved from command line arguments
    * @param configKey       A key in a configuration file
    * @param defaultValue    Global default value
    * @return The effective value of the parameter
    */
  private def getCmdOrConfigBoolean(cmdParameterOpt: Option[Boolean],
                                    configKey: String,
                                    defaultValue: Boolean): Boolean = {
    val enabled = cmdParameterOpt match {
      case Some(b) => b
      case None =>
        if (conf.hasPath(configKey)) {
          conf.getBoolean(configKey)
        } else {
          defaultValue
        }
    }
    enabled
  }

  private def obtainSparkSession(): SparkSession = {
    val spark: SparkSession = SparkSession.builder()
      .appName("Dynamic Conformance")
      .getOrCreate()

    TimeZoneNormalizer.normalizeSessionTimeZone(spark)
    spark
  }

  private def inferVersion(hdfsPublishPath: String, reportDate: String)
                          (implicit fsUtils: FileSystemVersionUtils):Int = {
    val newVersion = fsUtils.getLatestVersion(hdfsPublishPath, reportDate) + 1
    log.warn(s"Report version not provided, inferred report version: $newVersion")
    log.warn("This is an EXPERIMENTAL feature.")
    log.warn(" -> It can lead to issues when running multiple jobs on a dataset concurrently.")
    log.warn(" -> It may not work as desired when there are gaps in the versions of the data being landed.")
    newVersion
  }

  private def initFunctionalExtensions()(implicit spark: SparkSession): Unit = {
    import za.co.absa.spline.core.SparkLineageInitializer._ // Enable Spline
    spark.enableLineageTracking()

    import za.co.absa.atum.AtumImplicits.SparkSessionWrapper
    spark.enableControlMeasuresTracking().setControlMeasuresWorkflow("Conformance")
    Atum.setAllowUnpersistOldDatasets(true) // Enable control framework performance optimization for pipeline-like jobs
    MenasPlugin.enableMenas() // Enable Menas
  }

  private def initPerformanceMeasurer(stdPath: String)
                                     (implicit spark: SparkSession, fsUtils: FileSystemVersionUtils): PerformanceMeasurer = {
    // init performance measurer
    val performance = new PerformanceMeasurer(spark.sparkContext.appName)
    val stdDirSize = fsUtils.getDirectorySize(stdPath)
    performance.startMeasurement(stdDirSize)
    performance
  }

  private def conform(conformance: Dataset, inputData: sql.Dataset[Row], enableCF: Boolean)
                     (implicit spark: SparkSession, cmd: CmdConfig, fsUtils: FileSystemVersionUtils, dao: EnceladusDAO): DataFrame = {
    implicit val featureSwitcher: FeatureSwitches = FeatureSwitches()
      .setExperimentalMappingRuleEnabled(isExperimentalRuleEnabled())
      .setCatalystWorkaroundEnabled(isCatalystWorkaroundEnabled())
      .setControlFrameworkEnabled(enableCF)

    try {
      DynamicInterpreter.interpret(conformance, inputData)
    } catch {
      case e: ValidationException =>
        AtumImplicits.SparkSessionWrapper(spark).setControlMeasurementError("Conformance", e.getMessage, e.techDetails)
        throw e
      case NonFatal(e) =>
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))
        AtumImplicits.SparkSessionWrapper(spark).setControlMeasurementError("Conformance", e.getMessage, sw.toString)
        throw e
    }
  }

  private def processResult(result: DataFrame,
                            performance: PerformanceMeasurer,
                            pathCfg: PathCfg,
                            reportVersion: Int,
                            cmdLineArgs: String)
                           (implicit spark: SparkSession, cmd: CmdConfig, fsUtils: FileSystemVersionUtils): Unit = {
    import za.co.absa.enceladus.utils.implicits.DataFrameImplicits.DataFrameEnhancements
    val withPartCols = result
      .withColumnIfDoesNotExist(infoDateColumn, to_date(lit(cmd.reportDate), reportDateFormat))
      .withColumnIfDoesNotExist(infoDateColumnString, lit(cmd.reportDate))
      .withColumnIfDoesNotExist(infoVersionColumn, lit(reportVersion))

    val recordCount = result.lastCheckpointRowCount match {
      case None    => withPartCols.count
      case Some(p) => p
    }

    if (recordCount == 0) {
      val errMsg = "Empty output after running Dynamic Conformance."
      AtumImplicits.SparkSessionWrapper(spark).setControlMeasurementError("Conformance", errMsg, "")
      throw new IllegalStateException(errMsg)
    }
    // ensure the whole path but version exists
    fsUtils.createAllButLastSubDir(pathCfg.publishPath)

    withPartCols.write.parquet(pathCfg.publishPath)

    val publishDirSize = fsUtils.getDirectorySize(pathCfg.publishPath)
    performance.finishMeasurement(publishDirSize, recordCount)
    PerformanceMetricTools.addPerformanceMetricsToAtumMetadata(spark, "conform",
      pathCfg.stdPath, pathCfg.publishPath, EnceladusRestDAO.userName, cmdLineArgs)

    withPartCols.writeInfoFile(pathCfg.publishPath)
    cmd.performanceMetricsFile.foreach(fileName => {
      try {
        performance.writeMetricsToFile(fileName)
      } catch {
        case NonFatal(e) => log.error(s"Unable to write performance metrics to file '$fileName': ${e.getMessage}")
      }
    })
  }

  def buildPublishPath(infoDateCol: String,
      infoVersionCol: String,
      cmd: CmdConfig,
      ds: Dataset,
      reportVersion: Int): String = {
    (cmd.publishPathOverride, cmd.folderPrefix) match {
      case (None, None) =>
        s"${ds.hdfsPublishPath}/$infoDateCol=${cmd.reportDate}/$infoVersionCol=$reportVersion"
      case (None, Some(folderPrefix)) =>
        s"${ds.hdfsPublishPath}/$folderPrefix/$infoDateCol=${cmd.reportDate}/$infoVersionCol=$reportVersion"
      case (Some(publishPathOverride), _) =>
        publishPathOverride
    }
  }

  private final case class PathCfg(publishPath: String, stdPath: String)
}

