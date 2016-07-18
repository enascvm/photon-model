
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualHardwareCompatibilityIssue complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualHardwareCompatibilityIssue"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VmConfigFault"&gt;
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
@XmlType(name = "VirtualHardwareCompatibilityIssue")
@XmlSeeAlso({
    DrsVmotionIncompatibleFault.class,
    CpuIncompatible.class,
    FeatureRequirementsNotMet.class,
    DiskNotSupported.class,
    MemorySizeNotRecommended.class,
    MemorySizeNotSupported.class,
    MemorySizeNotSupportedByDatastore.class,
    NotEnoughCpus.class,
    NumVirtualCoresPerSocketNotSupported.class,
    NumVirtualCpusNotSupported.class,
    StorageVmotionIncompatible.class,
    DeviceNotSupported.class,
    VirtualHardwareVersionNotSupported.class,
    WakeOnLanNotSupported.class
})
public class VirtualHardwareCompatibilityIssue
    extends VmConfigFault
{


}
