/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jena.shex.reporting;

import org.apache.jena.shex.ShexStatus;

public class SimpleReport implements Report {

    private String message = "";
    private ShexStatus status = null;

    public SimpleReport(ShexStatus status, String message) {
        this.message = message;
        this.status = status;
    }

    @Override
    public ShexStatus getStatus() {
        return status;
    }

    protected SimpleReport() {}
    protected void setMessage(String message) {
        this.message = message;
    }
    protected void setStatus(ShexStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("%s %s",
                status == ShexStatus.conformant ? "OK" : "KO",
                message != null ? message : "");
    }
}
