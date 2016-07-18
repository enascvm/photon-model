
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CustomizationIpV6Generator complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CustomizationIpV6Generator"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
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
@XmlType(name = "CustomizationIpV6Generator")
@XmlSeeAlso({
    CustomizationDhcpIpV6Generator.class,
    CustomizationStatelessIpV6Generator.class,
    CustomizationFixedIpV6 .class,
    CustomizationAutoIpV6Generator.class,
    CustomizationUnknownIpV6Generator.class,
    CustomizationCustomIpV6Generator.class
})
public class CustomizationIpV6Generator
    extends DynamicData
{


}
