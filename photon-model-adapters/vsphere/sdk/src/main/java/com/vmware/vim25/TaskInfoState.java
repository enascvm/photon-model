
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TaskInfoState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="TaskInfoState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="queued"/&gt;
 *     &lt;enumeration value="running"/&gt;
 *     &lt;enumeration value="success"/&gt;
 *     &lt;enumeration value="error"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "TaskInfoState")
@XmlEnum
public enum TaskInfoState {

    @XmlEnumValue("queued")
    QUEUED("queued"),
    @XmlEnumValue("running")
    RUNNING("running"),
    @XmlEnumValue("success")
    SUCCESS("success"),
    @XmlEnumValue("error")
    ERROR("error");
    private final String value;

    TaskInfoState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static TaskInfoState fromValue(String v) {
        for (TaskInfoState c: TaskInfoState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
