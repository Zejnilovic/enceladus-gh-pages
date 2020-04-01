/*
 * Copyright 2018 ABSA Group Limited
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

import za.co.absa.hyperdrive.ingestor.api.{HasComponentAttributes, PropertyMetadata}

trait HyperConformanceAttributes extends HasComponentAttributes {

  val keysPrefix = "transformer.hyperconformance"

  // Configuration keys expected to be set up when running Conformance as a Transformer component for Hyperdrive
  val menasUriKey = s"$keysPrefix.menas.rest.uri"
  val menasCredentialsFileKey = s"$keysPrefix.menas.credentials.file"
  val menasAuthKeytabKey = s"$keysPrefix.menas.auth.keytab"
  val datasetNameKey = s"$keysPrefix.dataset.name"
  val datasetVersionKey = s"$keysPrefix.dataset.version"
  val reportDateKey = s"$keysPrefix.report.date"
  val reportVersionKey = s"$keysPrefix.report.version"

  override def getName: String = "HyperConformance"

  override def getDescription: String = "Dynamic Conformance - a transformer component for Hyperdrive"

  override def getProperties: Map[String, PropertyMetadata] = Map(
    menasUriKey -> PropertyMetadata("Menas API URL", Some("http://localhost:8080/menas"), required = true),
    datasetNameKey -> PropertyMetadata("A dataset name", None, required = true),
    datasetVersionKey -> PropertyMetadata("A dataset version", None, required = true),
    reportDateKey -> PropertyMetadata("A report date, the current date is used by default", None, required = false),
    reportVersionKey -> PropertyMetadata("A report version, will be determined automatically by default if not specified",
      None, required = false),
    menasCredentialsFileKey -> PropertyMetadata("A path to a Menas credentials file", None, required = false),
    menasAuthKeytabKey -> PropertyMetadata("A path to a keytab", None, required = false)
  )

  override def getExtraConfigurationPrefix: Option[String] = None
}
