/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.alexandreroman.cloudnative.stateless;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
class IndexController {
    private static final String ATT_COUNTER = "counter";
    private String hostName;

    @PostConstruct
    private void init() throws UnknownHostException {
        // Get host name.
        hostName = InetAddress.getLocalHost().getCanonicalHostName();
    }

    @GetMapping("/")
    public String inc(HttpSession session) {
        // Use HttpSession to store a counter:
        // we do not store value in instance members since
        // this app is stateless.
        AtomicInteger counter = (AtomicInteger) session.getAttribute(ATT_COUNTER);
        if (counter == null) {
            // Initialize counter to 0.
            counter = new AtomicInteger();
        }

        // Increment counter.
        final int counterValue = counter.getAndIncrement();
        // Store counter in session:
        // this value is actually stored using a Redis datastore.
        // All app instances share the same datastore.
        session.setAttribute(ATT_COUNTER, counter);

        return "Counter value from " + hostName + ": " + counterValue;
    }
}
