
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsFault"&gt;
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
@XmlType(name = "DvsFault")
@XmlSeeAlso({
    BackupBlobReadFailure.class,
    BackupBlobWriteFailure.class,
    CollectorAddressUnset.class,
    ConflictingConfiguration.class,
    DvsApplyOperationFault.class,
    DvsNotAuthorized.class,
    DvsOperationBulkFault.class,
    DvsScopeViolated.class,
    ImportHostAddFailure.class,
    ImportOperationBulkFault.class,
    InvalidIpfixConfig.class,
    RollbackFailure.class,
    SwitchIpUnset.class,
    SwitchNotInUpgradeMode.class,
    VspanDestPortConflict.class,
    VspanPortConflict.class,
    VspanPortMoveFault.class,
    VspanPortPromiscChangeFault.class,
    VspanPortgroupPromiscChangeFault.class,
    VspanPortgroupTypeChangeFault.class,
    VspanPromiscuousPortNotSupported.class,
    VspanSameSessionPortConflict.class
})
public class DvsFault
    extends VimFault
{


}
