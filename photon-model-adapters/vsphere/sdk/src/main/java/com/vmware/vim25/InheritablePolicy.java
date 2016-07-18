
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InheritablePolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InheritablePolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="inherited" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InheritablePolicy", propOrder = {
    "inherited"
})
@XmlSeeAlso({
    BoolPolicy.class,
    IntPolicy.class,
    LongPolicy.class,
    StringPolicy.class,
    DVSTrafficShapingPolicy.class,
    DVSVendorSpecificConfig.class,
    DvsFilterConfig.class,
    DvsFilterPolicy.class,
    VMwareUplinkPortOrderPolicy.class,
    DVSFailureCriteria.class,
    VmwareUplinkPortTeamingPolicy.class,
    VmwareDistributedVirtualSwitchVlanSpec.class,
    DVSSecurityPolicy.class,
    VMwareUplinkLacpPolicy.class
})
public class InheritablePolicy
    extends DynamicData
{

    protected boolean inherited;

    /**
     * Gets the value of the inherited property.
     * 
     */
    public boolean isInherited() {
        return inherited;
    }

    /**
     * Sets the value of the inherited property.
     * 
     */
    public void setInherited(boolean value) {
        this.inherited = value;
    }

}
