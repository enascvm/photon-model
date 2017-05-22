
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VVolVmConfigFileUpdateResultFailedVmConfigFileInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VVolVmConfigFileUpdateResultFailedVmConfigFileInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="targetConfigVVolId" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="fault" type="{urn:vim25}LocalizedMethodFault"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VVolVmConfigFileUpdateResultFailedVmConfigFileInfo", propOrder = {
    "targetConfigVVolId",
    "fault"
})
public class VVolVmConfigFileUpdateResultFailedVmConfigFileInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected String targetConfigVVolId;
    @XmlElement(required = true)
    protected LocalizedMethodFault fault;

    /**
     * Gets the value of the targetConfigVVolId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTargetConfigVVolId() {
        return targetConfigVVolId;
    }

    /**
     * Sets the value of the targetConfigVVolId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTargetConfigVVolId(String value) {
        this.targetConfigVVolId = value;
    }

    /**
     * Gets the value of the fault property.
     * 
     * @return
     *     possible object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public LocalizedMethodFault getFault() {
        return fault;
    }

    /**
     * Sets the value of the fault property.
     * 
     * @param value
     *     allowed object is
     *     {@link LocalizedMethodFault }
     *     
     */
    public void setFault(LocalizedMethodFault value) {
        this.fault = value;
    }

}
