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

package za.co.absa.enceladus.menas.controllers

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation._
import za.co.absa.enceladus.model.user.UserInfo
import za.co.absa.enceladus.menas.models.LandingPageInformation
import za.co.absa.enceladus.menas.repositories.DatasetMongoRepository
import za.co.absa.enceladus.menas.repositories.MappingTableMongoRepository
import za.co.absa.enceladus.menas.repositories.SchemaMongoRepository
import java.util.concurrent.CompletableFuture
import org.springframework.beans.factory.annotation.Autowired

@RestController 
@RequestMapping(Array("/api/landing"))
class LandingPageController @Autowired()(datasetRepository: DatasetMongoRepository, 
    mappingTableRepository: MappingTableMongoRepository,
    schemaRepository: SchemaMongoRepository) extends BaseController {

  import za.co.absa.enceladus.menas.utils.implicits._
  import scala.concurrent.ExecutionContext.Implicits.global
  
  @GetMapping(path = Array("/info"))
  def landingPageInfo(): CompletableFuture[LandingPageInformation] = {
    for {
      dsCount <- datasetRepository.distinctCount()
      mtCount <- mappingTableRepository.distinctCount()
      schemaCount <- schemaRepository.distinctCount()
    } yield LandingPageInformation(dsCount, mtCount, schemaCount)
  }

}
