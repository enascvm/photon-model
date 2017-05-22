
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterVmReadiness complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterVmReadiness"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="readyCondition" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="postReadyDelay" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterVmReadiness", propOrder = {
    "readyCondition",
    "postReadyDelay"
})
public class ClusterVmReadiness
    extends DynamicData
{

    protected String readyCondition;
    protected Integer postReadyDelay;

    /**
     * Gets the value of the readyCondition property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReadyCondition() {
        return readyCondition;
    }

    /**
     * Sets the value of the readyCondition property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReadyCondition(String value) {
        this.readyCondition = value;
    }

    /**
     * Gets the value of the postReadyDelay property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPostReadyDelay() {
        return postReadyDelay;
    }

    /**
     * Sets the value of the postReadyDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPostReadyDelay(Integer value) {
        this.postReadyDelay = value;
    }

}
