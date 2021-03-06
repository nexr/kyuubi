/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi.engine

import java.util.concurrent.CountDownLatch

import org.apache.kyuubi.{KyuubiSQLException, WithKyuubiServerOnYarn}
import org.apache.kyuubi.config.KyuubiConf
import org.apache.kyuubi.operation.JDBCTestUtils

class EngineSubmitSuite extends WithKyuubiServerOnYarn with JDBCTestUtils {
  override protected def jdbcUrl: String = getJdbcUrl
  override protected val kyuubiServerConf: KyuubiConf = KyuubiConf()
  override protected val connectionConf: Map[String, String] = {
    Map("spark.yarn.queue" -> "two_cores_queue",
      "spark.master" -> "yarn",
      "spark.submit.deployMode" -> "client",
      "spark.executor.instances" -> "1",
      "spark.driver.cores" -> "1",
      "spark.executor.cores" -> "1",
      KyuubiConf.ENGINE_SHARE_LEVEL.key -> "connection",
      KyuubiConf.ENGINE_INIT_TIMEOUT.key -> "60000")
  }

  test("submit spark app timeout with accepted status") {
    @volatile var appIsRunning = false
    val lock = new CountDownLatch(1)
    new Thread(() => {
      while (!appIsRunning) { Thread.sleep(100) }
      try {
        withJdbcStatement() { statement =>
          val exception = intercept[KyuubiSQLException] {
            statement.execute("select 1")
          }

          assert(exception.getMessage.contains("Failed to detect the root cause"))
          assert(exception.getMessage.contains("The last line log"))
          assert(exception.getMessage.contains("state: ACCEPTED"))
        }
      } finally {
        lock.countDown()
      }
    }).start()

    withJdbcStatement() { statement =>
      appIsRunning = true
      statement.execute("select 1")
      // hold resource so that the queue has no resource for other app
      lock.await()
    }
  }
}
