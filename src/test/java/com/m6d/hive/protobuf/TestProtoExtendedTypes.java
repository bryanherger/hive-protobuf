package com.m6d.hive.protobuf;
import com.jointhegrid.hive_test.HiveTestService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.junit.Assert;

import prototest.Ex;
import prototest.Ex.ExtendedTypes;
import prototest.Ex.ExtendedTypes.SecretCipher;
import com.google.protobuf.ByteString;

public class TestProtoExtendedTypes extends HiveTestService{

  public TestProtoExtendedTypes() throws IOException {
    super();
  }

  public void testExtendedTypes() throws Exception {
    String table="extendedtypes";
        Path p = new Path(this.ROOT_DIR, table);
    SequenceFile.Writer w = SequenceFile.createWriter(this.getFileSystem(),
            new Configuration(), p, BytesWritable.class, BytesWritable.class);

    ExtendedTypes.Builder et = ExtendedTypes.newBuilder();
    et.setCipher(SecretCipher.Foo);
    et.setSecretMessage(ByteString.copyFromUtf8("aBinaryString"));
    
    BytesWritable key = new BytesWritable();
    BytesWritable value = new BytesWritable();
    ByteArrayOutputStream s = new ByteArrayOutputStream();
    et.build().writeTo(s);

    ByteArrayOutputStream t = new ByteArrayOutputStream();
    et.build().writeTo(t);
    
    key.set(s.toByteArray(), 0, s.size());
    value.set(t.toByteArray(), 0, t.size());
    w.append(key, value);
    w.close();

    String jarFile;
    jarFile = KVAsVSeqFileBinaryInputFormat.class.getProtectionDomain().getCodeSource().getLocation().getFile();

    client.execute("add jar " + jarFile);
    client.execute("set hive.aux.jars.path=file:///"+jarFile);

    client.execute("create table     "+table+" "
            + " ROW FORMAT SERDE '" + ProtobufDeserializer.class.getName() + "'"
            + " WITH SERDEPROPERTIES ('KEY_SERIALIZE_CLASS'='" + Ex.ExtendedTypes.class.getName()+ "','VALUE_SERIALIZE_CLASS'='" + Ex.ExtendedTypes.class.getName()+ "'   )"
            + " STORED AS INPUTFORMAT '" + KVAsVSeqFileBinaryInputFormat.class.getName() + "'"
            + " OUTPUTFORMAT '" + SequenceFileOutputFormat.class.getName() + "'");

    client.execute("load data local inpath '" + p.toString() + "' into table "+table+" ");
    client.execute("DESCRIBE "+table);
    List<String> results = client.fetchAll();
    Assert.assertEquals(2, results.size());
    String expected="key struct<cipher:string,secretmessage:binary> from deserializer";
    Assert.assertEquals(expected, results.get(0).replaceAll("\\s+", " "));
    expected="value struct<cipher:string,secretmessage:binary> from deserializer";
    Assert.assertEquals(expected, results.get(1).replaceAll("\\s+", " "));
    client.execute("SELECT value FROM "+table);
    results = client.fetchAll();
    expected="{\"cipher\":\"Foo\",\"secretmessage\":aBinaryString}";
    System.out.println(expected);
    System.out.println(results.get(0));
    Assert.assertEquals(expected, results.get(0));
    client.execute("drop table "+table);

  }

}