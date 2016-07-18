
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for NotSupportedHostInCluster complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NotSupportedHostInCluster"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}NotSupportedHost"&gt;
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
@XmlType(name = "NotSupportedHostInCluster")
@XmlSeeAlso({
    EVCAdmissionFailed.class
})
public class NotSupportedHostInCluster
    extends NotSupportedHost
{


}
