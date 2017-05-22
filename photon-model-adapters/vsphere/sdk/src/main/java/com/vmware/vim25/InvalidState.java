
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidState complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InvalidState"&gt;
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
@XmlType(name = "InvalidState")
@XmlSeeAlso({
    CannotPowerOffVmInCluster.class,
    InvalidDatastoreState.class,
    InvalidHostState.class,
    InvalidPowerState.class,
    InvalidVmState.class,
    MksConnectionLimitReached.class,
    NoActiveHostInCluster.class,
    OvfConsumerPowerOnFault.class,
    QuestionPending.class,
    VmPowerOnDisabled.class
})
public class InvalidState
    extends VimFault
{


}
