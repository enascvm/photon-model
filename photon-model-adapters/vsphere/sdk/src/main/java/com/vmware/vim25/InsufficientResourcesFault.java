
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InsufficientResourcesFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InsufficientResourcesFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
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
@XmlType(name = "InsufficientResourcesFault")
@XmlSeeAlso({
    InsufficientAgentVmsDeployed.class,
    InsufficientCpuResourcesFault.class,
    InsufficientFailoverResourcesFault.class,
    InsufficientGraphicsResourcesFault.class,
    InsufficientMemoryResourcesFault.class,
    InsufficientNetworkCapacity.class,
    InsufficientNetworkResourcePoolCapacity.class,
    InsufficientHostCapacityFault.class,
    InsufficientStandbyResource.class,
    InsufficientStorageSpace.class,
    InsufficientVFlashResourcesFault.class,
    InvalidResourcePoolStructureFault.class,
    NumVirtualCpusExceedsLimit.class,
    VmFaultToleranceTooManyFtVcpusOnHost.class,
    VmFaultToleranceTooManyVMsOnHost.class,
    VmSmpFaultToleranceTooManyVMsOnHost.class
})
public class InsufficientResourcesFault
    extends VimFault
{


}
