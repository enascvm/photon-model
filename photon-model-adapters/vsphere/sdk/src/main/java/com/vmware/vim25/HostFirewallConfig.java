
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFirewallConfig complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostFirewallConfig"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="rule" type="{urn:vim25}HostFirewallConfigRuleSetConfig" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="defaultBlockingPolicy" type="{urn:vim25}HostFirewallDefaultPolicy"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostFirewallConfig", propOrder = {
    "rule",
    "defaultBlockingPolicy"
})
public class HostFirewallConfig
    extends DynamicData
{

    protected List<HostFirewallConfigRuleSetConfig> rule;
    @XmlElement(required = true)
    protected HostFirewallDefaultPolicy defaultBlockingPolicy;

    /**
     * Gets the value of the rule property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the rule property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRule().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostFirewallConfigRuleSetConfig }
     * 
     * 
     */
    public List<HostFirewallConfigRuleSetConfig> getRule() {
        if (rule == null) {
            rule = new ArrayList<HostFirewallConfigRuleSetConfig>();
        }
        return this.rule;
    }

    /**
     * Gets the value of the defaultBlockingPolicy property.
     * 
     * @return
     *     possible object is
     *     {@link HostFirewallDefaultPolicy }
     *     
     */
    public HostFirewallDefaultPolicy getDefaultBlockingPolicy() {
        return defaultBlockingPolicy;
    }

    /**
     * Sets the value of the defaultBlockingPolicy property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostFirewallDefaultPolicy }
     *     
     */
    public void setDefaultBlockingPolicy(HostFirewallDefaultPolicy value) {
        this.defaultBlockingPolicy = value;
    }

}
