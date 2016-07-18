
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmwareDistributedVirtualSwitchVlanSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmwareDistributedVirtualSwitchVlanSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InheritablePolicy"&gt;
 *       &lt;sequence&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmwareDistributedVirtualSwitchVlanSpec")
@XmlSeeAlso({
    VmwareDistributedVirtualSwitchPvlanSpec.class,
    VmwareDistributedVirtualSwitchVlanIdSpec.class,
    VmwareDistributedVirtualSwitchTrunkVlanSpec.class
})
public class VmwareDistributedVirtualSwitchVlanSpec
    extends InheritablePolicy
{


}
