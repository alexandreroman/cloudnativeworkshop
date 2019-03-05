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

package fr.alexandreroman.cloudnative.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
@RefreshScope
class HelloController {
    @Value("${message:Hello default!}")
    private String message;

    @GetMapping("/")
    public String hello() {
        return message;
    }
}

@Configuration
class SecurityConfig extends WebSecurityConfigurerAdapter {
    // Adding Spring Cloud Config Server support activates
    // a default security configuration.
    // You may need to tune the default security configuration.

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Disable security access checks.
        http.authorizeRequests().anyRequest().permitAll()
                .and()
                .httpBasic().disable()
                .csrf().disable();
    }
}
