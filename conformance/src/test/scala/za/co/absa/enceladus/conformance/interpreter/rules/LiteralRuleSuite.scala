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

package za.co.absa.enceladus.conformance.interpreter.rules

import org.mockito.Mockito.{mock, when => mockWhen}
import org.scalatest.FunSuite
import za.co.absa.enceladus.conformance.CmdConfig
import za.co.absa.enceladus.conformance.interpreter.DynamicInterpreter
import za.co.absa.enceladus.dao.EnceladusDAO
import za.co.absa.enceladus.samples.DeepArraySamples
import za.co.absa.enceladus.utils.testUtils.SparkTestBase

class LiteralRuleSuite extends FunSuite with SparkTestBase {

  test("Literal conformance rule test 1") {
    val inputDf = spark.createDataFrame(DeepArraySamples.ordersData)

    spark.conf.set("spark.sql.session.timeZone", "GMT")

    inputDf.printSchema()
    inputDf.show

    implicit val dao: EnceladusDAO = mock(classOf[EnceladusDAO])
    implicit val progArgs: CmdConfig = CmdConfig(reportDate = "2017-11-01")
    implicit val enableCF: Boolean = false

    mockWhen (dao.getDataset("Orders Conformance", 1)) thenReturn DeepArraySamples.literalOrdersDS1

    val mappingTablePattern = "{0}/{1}/{2}"

    import spark.implicits._
    val conformed = DynamicInterpreter.interpret(DeepArraySamples.literalOrdersDS1, inputDf).cache

    conformed.printSchema()
    conformed.show

    val conformedJSON = conformed.orderBy($"id").toJSON.collect().mkString("\n")

    if (conformedJSON != DeepArraySamples.conformedLiteralOrdersJSON1) {
      println("EXPECTED:")
      println(DeepArraySamples.conformedLiteralOrdersJSON1)
      println("ACTUAL:")
      println(conformedJSON)
      fail("Actual conformed dataset JSON does not match the expected JSON (see above).")
    }

  }

  test("Literal conformance rule test 2") {
    val inputDf = spark.createDataFrame(DeepArraySamples.ordersData)

    spark.conf.set("spark.sql.session.timeZone", "GMT")

    inputDf.printSchema()
    inputDf.show

    implicit val dao: EnceladusDAO = mock(classOf[EnceladusDAO])
    implicit val progArgs: CmdConfig = CmdConfig(reportDate = "2017-11-01")
    implicit val enableCF: Boolean = false

    mockWhen (dao.getDataset("Orders Conformance", 1)) thenReturn DeepArraySamples.literalOrdersDS2

    val mappingTablePattern = "{0}/{1}/{2}"

    import spark.implicits._
    val conformed = DynamicInterpreter.interpret(DeepArraySamples.literalOrdersDS2, inputDf).cache

    conformed.printSchema()
    conformed.show

    val conformedJSON = conformed.orderBy($"id").toJSON.collect().mkString("\n")

    if (conformedJSON != DeepArraySamples.conformedLiteralOrdersJSON2) {
      println("EXPECTED:")
      println(DeepArraySamples.conformedLiteralOrdersJSON2)
      println("ACTUAL:")
      println(conformedJSON)
      fail("Actual conformed dataset JSON does not match the expected JSON (see above).")
    }

  }

  test("Literal conformance rule test 3") {
    val inputDf = spark.createDataFrame(DeepArraySamples.ordersData)

    spark.conf.set("spark.sql.session.timeZone", "GMT")

    inputDf.printSchema()
    inputDf.show

    implicit val dao: EnceladusDAO = mock(classOf[EnceladusDAO])
    implicit val progArgs: CmdConfig = CmdConfig(reportDate = "2017-11-01")
    implicit val enableCF: Boolean = false

    mockWhen(dao.getDataset("Orders Conformance", 1)) thenReturn DeepArraySamples.literalOrdersDS3

    val mappingTablePattern = "{0}/{1}/{2}"

    import spark.implicits._
    val conformed = DynamicInterpreter.interpret(DeepArraySamples.literalOrdersDS3, inputDf).cache

    conformed.printSchema()
    conformed.show

    val conformedJSON = conformed.orderBy($"id").toJSON.collect().mkString("\n")

    if (conformedJSON != DeepArraySamples.conformedLiteralOrdersJSON3) {
      println("EXPECTED:")
      println(DeepArraySamples.conformedLiteralOrdersJSON3)
      println("ACTUAL:")
      println(conformedJSON)
      fail("Actual conformed dataset JSON does not match the expected JSON (see above).")
    }

  }

}
