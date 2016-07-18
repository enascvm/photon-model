
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanHostClusterStatusState complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VsanHostClusterStatusState"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="state" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="completion" type="{urn:vim25}VsanHostClusterStatusStateCompletionEstimate" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VsanHostClusterStatusState", propOrder = {
    "state",
    "completion"
})
public class VsanHostClusterStatusState
    extends DynamicData
{

    @XmlElement(required = true)
    protected String state;
    protected VsanHostClusterStatusStateCompletionEstimate completion;

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

    /**
     * Gets the value of the completion property.
     * 
     * @return
     *     possible object is
     *     {@link VsanHostClusterStatusStateCompletionEstimate }
     *     
     */
    public VsanHostClusterStatusStateCompletionEstimate getCompletion() {
        return completion;
    }

    /**
     * Sets the value of the completion property.
     * 
     * @param value
     *     allowed object is
     *     {@link VsanHostClusterStatusStateCompletionEstimate }
     *     
     */
    public void setCompletion(VsanHostClusterStatusStateCompletionEstimate value) {
        this.completion = value;
    }

}
