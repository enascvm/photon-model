
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VchaClusterConfigInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VchaClusterConfigInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="failoverNodeInfo1" type="{urn:vim25}FailoverNodeInfo" minOccurs="0"/&gt;
 *         &lt;element name="failoverNodeInfo2" type="{urn:vim25}FailoverNodeInfo" minOccurs="0"/&gt;
 *         &lt;element name="witnessNodeInfo" type="{urn:vim25}WitnessNodeInfo" minOccurs="0"/&gt;
 *         &lt;element name="state" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VchaClusterConfigInfo", propOrder = {
    "failoverNodeInfo1",
    "failoverNodeInfo2",
    "witnessNodeInfo",
    "state"
})
public class VchaClusterConfigInfo
    extends DynamicData
{

    protected FailoverNodeInfo failoverNodeInfo1;
    protected FailoverNodeInfo failoverNodeInfo2;
    protected WitnessNodeInfo witnessNodeInfo;
    @XmlElement(required = true)
    protected String state;

    /**
     * Gets the value of the failoverNodeInfo1 property.
     * 
     * @return
     *     possible object is
     *     {@link FailoverNodeInfo }
     *     
     */
    public FailoverNodeInfo getFailoverNodeInfo1() {
        return failoverNodeInfo1;
    }

    /**
     * Sets the value of the failoverNodeInfo1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link FailoverNodeInfo }
     *     
     */
    public void setFailoverNodeInfo1(FailoverNodeInfo value) {
        this.failoverNodeInfo1 = value;
    }

    /**
     * Gets the value of the failoverNodeInfo2 property.
     * 
     * @return
     *     possible object is
     *     {@link FailoverNodeInfo }
     *     
     */
    public FailoverNodeInfo getFailoverNodeInfo2() {
        return failoverNodeInfo2;
    }

    /**
     * Sets the value of the failoverNodeInfo2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link FailoverNodeInfo }
     *     
     */
    public void setFailoverNodeInfo2(FailoverNodeInfo value) {
        this.failoverNodeInfo2 = value;
    }

    /**
     * Gets the value of the witnessNodeInfo property.
     * 
     * @return
     *     possible object is
     *     {@link WitnessNodeInfo }
     *     
     */
    public WitnessNodeInfo getWitnessNodeInfo() {
        return witnessNodeInfo;
    }

    /**
     * Sets the value of the witnessNodeInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link WitnessNodeInfo }
     *     
     */
    public void setWitnessNodeInfo(WitnessNodeInfo value) {
        this.witnessNodeInfo = value;
    }

    /**
     * Gets the value of the state property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getState() {
        return state;
    }

    /**
     * Sets the value of the state property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setState(String value) {
        this.state = value;
    }

}
