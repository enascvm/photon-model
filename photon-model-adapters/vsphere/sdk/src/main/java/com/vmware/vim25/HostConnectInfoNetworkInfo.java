
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostConnectInfoNetworkInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostConnectInfoNetworkInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="summary" type="{urn:vim25}NetworkSummary"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostConnectInfoNetworkInfo", propOrder = {
    "summary"
})
@XmlSeeAlso({
    HostNewNetworkConnectInfo.class
})
public class HostConnectInfoNetworkInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected NetworkSummary summary;

    /**
     * Gets the value of the summary property.
     * 
     * @return
     *     possible object is
     *     {@link NetworkSummary }
     *     
     */
    public NetworkSummary getSummary() {
        return summary;
    }

    /**
     * Sets the value of the summary property.
     * 
     * @param value
     *     allowed object is
     *     {@link NetworkSummary }
     *     
     */
    public void setSummary(NetworkSummary value) {
        this.summary = value;
    }

}
