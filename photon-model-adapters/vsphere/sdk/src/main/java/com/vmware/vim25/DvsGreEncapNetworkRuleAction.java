
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsGreEncapNetworkRuleAction complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsGreEncapNetworkRuleAction"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsNetworkRuleAction"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="encapsulationIp" type="{urn:vim25}SingleIp"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvsGreEncapNetworkRuleAction", propOrder = {
    "encapsulationIp"
})
public class DvsGreEncapNetworkRuleAction
    extends DvsNetworkRuleAction
{

    @XmlElement(required = true)
    protected SingleIp encapsulationIp;

    /**
     * Gets the value of the encapsulationIp property.
     * 
     * @return
     *     possible object is
     *     {@link SingleIp }
     *     
     */
    public SingleIp getEncapsulationIp() {
        return encapsulationIp;
    }

    /**
     * Sets the value of the encapsulationIp property.
     * 
     * @param value
     *     allowed object is
     *     {@link SingleIp }
     *     
     */
    public void setEncapsulationIp(SingleIp value) {
        this.encapsulationIp = value;
    }

}
