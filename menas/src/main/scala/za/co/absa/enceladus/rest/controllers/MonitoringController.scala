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

package za.co.absa.enceladus.rest.controllers

import java.util.concurrent.CompletableFuture

import org.mongodb.scala.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation._
import org.springframework.web.multipart.MultipartFile
import za.co.absa.enceladus.model.Run
import za.co.absa.enceladus.model.menas._
import za.co.absa.enceladus.rest.repositories.RefCollection
import za.co.absa.enceladus.rest.services.MonitoringService
import za.co.absa.enceladus.rest.models.MonitoringDataPoint
import za.co.absa.enceladus.rest.utils.converters.SparkMenasSchemaConvertor

@RestController
@RequestMapping(Array("/api/monitoring"))
class MonitoringController @Autowired()(monitoringService: MonitoringService)
  extends BaseController {

  import za.co.absa.enceladus.rest.utils.implicits._

  import scala.concurrent.ExecutionContext.Implicits.global

  @GetMapping(Array("/{datasetName}"))
  @ResponseStatus(HttpStatus.OK)
  def getJson(@PathVariable datasetName: String): CompletableFuture[Seq[String]] = {
    monitoringService.getMonitoringDataPoints(datasetName)
  }

}
