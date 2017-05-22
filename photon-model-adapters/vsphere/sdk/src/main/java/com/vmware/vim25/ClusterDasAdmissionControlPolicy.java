
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterDasAdmissionControlPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterDasAdmissionControlPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="resourceReductionToToleratePercent" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterDasAdmissionControlPolicy", propOrder = {
    "resourceReductionToToleratePercent"
})
@XmlSeeAlso({
    ClusterFailoverHostAdmissionControlPolicy.class,
    ClusterFailoverLevelAdmissionControlPolicy.class,
    ClusterFailoverResourcesAdmissionControlPolicy.class
})
public class ClusterDasAdmissionControlPolicy
    extends DynamicData
{

    protected Integer resourceReductionToToleratePercent;

    /**
     * Gets the value of the resourceReductionToToleratePercent property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getResourceReductionToToleratePercent() {
        return resourceReductionToToleratePercent;
    }

    /**
     * Sets the value of the resourceReductionToToleratePercent property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setResourceReductionToToleratePercent(Integer value) {
        this.resourceReductionToToleratePercent = value;
    }

}
