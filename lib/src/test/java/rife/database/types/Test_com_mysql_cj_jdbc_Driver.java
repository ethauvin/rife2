/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.database.types;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.Test;
import rife.database.SomeEnum;
import rife.database.types.databasedrivers.com_mysql_cj_jdbc_Driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Test_com_mysql_cj_jdbc_Driver {
    @Test
    void testGetSqlValue() {
        SqlConversion dbtypes;
        dbtypes = new com_mysql_cj_jdbc_Driver();

        assertNotNull(dbtypes);
        assertEquals(dbtypes.getSqlValue(null), "NULL");
        assertEquals(dbtypes.getSqlValue("fdjh'kjhsdf'"), "'fdjh''kjhsdf'''");
        assertEquals(dbtypes.getSqlValue(new StringBuffer("kkdfkj'jfoodf")), "'kkdfkj''jfoodf'");
        assertEquals(dbtypes.getSqlValue('K'), "'K'");
        assertEquals(dbtypes.getSqlValue('\''), "''''");
        Calendar cal = Calendar.getInstance();
        cal.set(2002, 05, 18, 18, 45, 40);
        assertEquals(dbtypes.getSqlValue(new Time(cal.getTime().getTime())), "'18:45:40'");
        assertEquals(dbtypes.getSqlValue(new Timestamp(cal.getTime().getTime())), "'2002-06-18 18:45:40.0'");
        assertEquals(dbtypes.getSqlValue(new java.sql.Date(cal.getTime().getTime())), "'2002-06-18'");
        assertEquals(dbtypes.getSqlValue(new Date(cal.getTime().getTime())), "'2002-06-18 18:45:40.0'");
        assertEquals(dbtypes.getSqlValue(cal), "'2002-06-18 18:45:40.0'");
        assertEquals(dbtypes.getSqlValue(new String[]{"kjkdf", "fdfdf", "ljkldfd"}), "{'kjkdf','fdfdf','ljkldfd'}");
        assertEquals(dbtypes.getSqlValue(Boolean.TRUE), "1");
        assertEquals(dbtypes.getSqlValue(SomeEnum.VALUE_TWO), "'VALUE_TWO'");
    }
}
