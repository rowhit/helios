/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.common.descriptors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.helios.common.Hash;
import com.spotify.helios.common.Json;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.spotify.helios.common.descriptors.Descriptor.parse;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class JobTest {

  private Map<String, Object> map(final Object... objects) {
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    checkArgument(objects.length % 2 == 0);
    for (int i = 0; i < objects.length; i += 2) {
      builder.put((String) objects[i], objects[i + 1]);
    }
    return builder.build();
  }

  @Test
  public void verifyBuilder() throws Exception {
    final Job.Builder builder = Job.newBuilder();

    // Input to setXXX
    final String setName = "set_name";
    final String setVersion = "set_version";
    final String setImage = "set_image";
    final List<String> setCommand = asList("set", "command");
    final Map<String, String> setEnv = ImmutableMap.of("set", "env");
    final Map<String, PortMapping> setPorts = ImmutableMap.of("set_ports", PortMapping.of(1234));
    final Map<ServiceEndpoint, ServicePorts> setRegistration = ImmutableMap.of(
        ServiceEndpoint.of("set_service", "set_proto"),
        ServicePorts.of("set_ports1", "set_ports2"));

    // Input to addXXX
    final Map<String, String> addEnv = ImmutableMap.of("add", "env");
    final Map<String, PortMapping> addPorts = ImmutableMap.of("add_ports", PortMapping.of(4711));
    final Map<ServiceEndpoint, ServicePorts> addRegistration = ImmutableMap.of(
        ServiceEndpoint.of("add_service", "add_proto"),
        ServicePorts.of("add_ports1", "add_ports2"));

    // Expected output from getXXX
    final String expectedName = setName;
    final String expectedVersion = setVersion;
    final String expectedImage = setImage;
    final List<String> expectedCommand = setCommand;
    final Map<String, String> expectedEnv = concat(setEnv, addEnv);
    final Map<String, PortMapping> expectedPorts = concat(setPorts, addPorts);
    final Map<ServiceEndpoint, ServicePorts> expectedRegistration = concat(setRegistration,
                                                                           addRegistration);

    // Check setXXX methods
    builder.setName(setName);
    builder.setVersion(setVersion);
    builder.setImage(setImage);
    builder.setCommand(setCommand);
    builder.setEnv(setEnv);
    builder.setPorts(setPorts);
    builder.setRegistration(setRegistration);
    assertEquals("name", setName, builder.getName());
    assertEquals("version", setVersion, builder.getVersion());
    assertEquals("image", setImage, builder.getImage());
    assertEquals("command", setCommand, builder.getCommand());
    assertEquals("env", setEnv, builder.getEnv());
    assertEquals("ports", setPorts, builder.getPorts());
    assertEquals("registration", setRegistration, builder.getRegistration());

    // Check addXXX methods
    for (final Map.Entry<String, String> entry : addEnv.entrySet()) {
      builder.addEnv(entry.getKey(), entry.getValue());
    }
    for (final Map.Entry<String, PortMapping> entry : addPorts.entrySet()) {
      builder.addPort(entry.getKey(), entry.getValue());
    }
    for (final Map.Entry<ServiceEndpoint, ServicePorts> entry : addRegistration.entrySet()) {
      builder.addRegistration(entry.getKey(), entry.getValue());
    }
    assertEquals("name", expectedName, builder.getName());
    assertEquals("version", expectedVersion, builder.getVersion());
    assertEquals("image", expectedImage, builder.getImage());
    assertEquals("command", expectedCommand, builder.getCommand());
    assertEquals("env", expectedEnv, builder.getEnv());
    assertEquals("ports", expectedPorts, builder.getPorts());
    assertEquals("registration", expectedRegistration, builder.getRegistration());

    // Check final output
    final Job job = builder.build();
    assertEquals("name", expectedName, job.getId().getName());
    assertEquals("version", expectedVersion, job.getId().getVersion());
    assertEquals("image", expectedImage, job.getImage());
    assertEquals("command", expectedCommand, job.getCommand());
    assertEquals("env", expectedEnv, job.getEnv());
    assertEquals("ports", expectedPorts, job.getPorts());
    assertEquals("registration", expectedRegistration, job.getRegistration());

    // Check clone
    final Job.Builder cloned = builder.clone();
    assertEquals("name", expectedName, cloned.getName());
    assertEquals("version", expectedVersion, cloned.getVersion());
    assertEquals("image", expectedImage, cloned.getImage());
    assertEquals("command", expectedCommand, cloned.getCommand());
    assertEquals("env", expectedEnv, cloned.getEnv());
    assertEquals("ports", expectedPorts, cloned.getPorts());
    assertEquals("registration", expectedRegistration, cloned.getRegistration());

    final Job clonedJob = cloned.build();
    assertEquals("name", expectedName, clonedJob.getId().getName());
    assertEquals("version", expectedVersion, clonedJob.getId().getVersion());
    assertEquals("image", expectedImage, clonedJob.getImage());
    assertEquals("command", expectedCommand, clonedJob.getCommand());
    assertEquals("env", expectedEnv, clonedJob.getEnv());
    assertEquals("ports", expectedPorts, clonedJob.getPorts());
    assertEquals("registration", expectedRegistration, clonedJob.getRegistration());
  }

  @SafeVarargs
  private final <K, V> Map<K, V> concat(final Map<K, V>... maps) {
    final ImmutableMap.Builder<K, V> b = ImmutableMap.builder();
    for (final Map<K, V> map : maps) {
      b.putAll(map);
    }
    return b.build();
  }

  @Test
  public void verifySha1ID() throws IOException {
    final Map<String, Object> expectedConfig = map("command", asList("foo", "bar"),
                                                   "image", "foobar:4711",
                                                   "name", "foozbarz",
                                                   "version", "17");

    final String expectedInput = "foozbarz:17:" + hex(Json.sha1digest(expectedConfig));
    final String expectedDigest = hex(Hash.sha1digest(expectedInput.getBytes(UTF_8)));
    final JobId expectedId = JobId.fromString("foozbarz:17:" + expectedDigest);

    final Job job = Job.newBuilder()
        .setCommand(asList("foo", "bar"))
        .setImage("foobar:4711")
        .setName("foozbarz")
        .setVersion("17")
        .build();

    assertEquals(expectedId, job.getId());
  }

  @Test
  public void verifySha1IDWithEnv() throws IOException {
    final Map<String, String> env = ImmutableMap.of("FOO", "BAR");
    final Map<String, Object> expectedConfig = map("command", asList("foo", "bar"),
                                                   "image", "foobar:4711",
                                                   "name", "foozbarz",
                                                   "version", "17",
                                                   "env", env);

    final String expectedInput = "foozbarz:17:" + hex(Json.sha1digest(expectedConfig));
    final String expectedDigest = hex(Hash.sha1digest(expectedInput.getBytes(UTF_8)));
    final JobId expectedId = JobId.fromString("foozbarz:17:" + expectedDigest);

    final Job job = Job.newBuilder()
        .setCommand(asList("foo", "bar"))
        .setImage("foobar:4711")
        .setName("foozbarz")
        .setVersion("17")
        .setEnv(env)
        .build();

    assertEquals(expectedId, job.getId());
  }

  private String hex(final byte[] bytes) {
    return BaseEncoding.base16().lowerCase().encode(bytes);
  }

  @Test
  public void verifyCanParseJobWithUnknownFields() throws Exception {
    final Job job = Job.newBuilder()
        .setCommand(asList("foo", "bar"))
        .setImage("foobar:4711")
        .setName("foozbarz")
        .setVersion("17")
        .build();

    final String jobJson = job.toJsonString();

    final ObjectMapper objectMapper = new ObjectMapper();
    final Map<String, Object> fields = objectMapper.readValue(
        jobJson, new TypeReference<Map<String, Object>>() {});
    fields.put("UNKNOWN_FIELD", "FOOBAR");
    final String modifiedJobJson = objectMapper.writeValueAsString(fields);

    final Job parsedJob = parse(modifiedJobJson, Job.class);

    assertEquals(job, parsedJob);
  }

  @Test
  public void verifyCanParseJobWithMissingEnv() throws Exception {
    final Job job = Job.newBuilder()
        .setCommand(asList("foo", "bar"))
        .setImage("foobar:4711")
        .setName("foozbarz")
        .setVersion("17")
        .build();

    final String jobJson = job.toJsonString();

    final ObjectMapper objectMapper = new ObjectMapper();
    final Map<String, Object> fields = objectMapper.readValue(
        jobJson, new TypeReference<Map<String, Object>>() {});
    fields.remove("env");
    final String modifiedJobJson = objectMapper.writeValueAsString(fields);

    final Job parsedJob = parse(modifiedJobJson, Job.class);

    assertEquals(job, parsedJob);
  }

  @Test
  public void verifyJobIsImmutable() {
    final List<String> expectedCommand = ImmutableList.of("foo");
    final Map<String, String> expectedEnv = ImmutableMap.of("e1", "1");
    final Map<String, PortMapping> expectedPorts = ImmutableMap.of("p1", PortMapping.of(1, 2));
    final Map<ServiceEndpoint, ServicePorts> expectedRegistration =
        ImmutableMap.of(ServiceEndpoint.of("foo", "tcp"), ServicePorts.of("p1"));

    final List<String> mutableCommand = Lists.newArrayList(expectedCommand);
    final Map<String, String> mutableEnv = Maps.newHashMap(expectedEnv);
    final Map<String, PortMapping> mutablePorts = Maps.newHashMap(expectedPorts);
    final HashMap<ServiceEndpoint, ServicePorts> mutableRegistration =
        Maps.newHashMap(expectedRegistration);

    final Job.Builder builder = Job.newBuilder()
        .setCommand(mutableCommand)
        .setEnv(mutableEnv)
        .setPorts(mutablePorts)
        .setImage("foobar:4711")
        .setName("foozbarz")
        .setVersion("17")
        .setRegistration(mutableRegistration);

    final Job job = builder.build();

    mutableCommand.add("bar");
    mutableEnv.put("e2", "2");
    mutablePorts.put("p2", PortMapping.of(3, 4));
    mutableRegistration.put(ServiceEndpoint.of("bar", "udp"), ServicePorts.of("p2"));

    builder.addPort("added_port", PortMapping.of(4711));
    builder.addEnv("added_env", "FOO");
    builder.addRegistration(ServiceEndpoint.of("added_reg", "added_proto"),
                            ServicePorts.of("added_port"));

    assertEquals(expectedCommand, job.getCommand());
    assertEquals(expectedEnv, job.getEnv());
    assertEquals(expectedPorts, job.getPorts());
    assertEquals(expectedRegistration, job.getRegistration());
  }
}
