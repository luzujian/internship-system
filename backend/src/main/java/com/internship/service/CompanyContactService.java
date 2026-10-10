package com.internship.service;

import com.internship.entity.CompanyContact;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CompanyContactService {
    private final CompanyContactMapper mapper;
    private final PositionCacheService positionCacheService;

    public List<CompanyContact> list(Long companyId) { return mapper.findByCompanyId(companyId); }

    // All contact writes and position contact assignments use the same company lock.
    public void lockCompany(Long companyId) {
        if (companyId == null || mapper.lockCompany(companyId) == null) {
            throw new AccessDeniedException("企业身份无效");
        }
    }

    public void requireOwned(Long companyId, Long contactId) {
        if (contactId != null && mapper.findOwned(contactId, companyId) == null) {
            throw new BusinessException("所选联系人不存在或不属于本企业");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyContact save(Long companyId, Long id, CompanyContact contact) {
        lockCompany(companyId);
        requireOwned(companyId, id);
        normalize(contact);
        for (CompanyContact existing : list(companyId)) {
            if (!Objects.equals(id, existing.getId()) && existing.getName().equals(contact.getName())
                    && existing.getPhone().equals(contact.getPhone())) {
                throw new BusinessException("该联系人已存在，请编辑已有联系人");
            }
        }
        contact.setId(id);
        contact.setCompanyId(companyId);
        if (id == null) mapper.insert(contact); else mapper.update(contact);
        invalidatePositions();
        return contact;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long companyId, Long id) {
        lockCompany(companyId);
        requireOwned(companyId, id);
        if (mapper.countPositions(id) > 0) {
            throw new BusinessException("该联系人正在被岗位使用，请先更换岗位联系人再删除");
        }
        mapper.delete(id, companyId);
        invalidatePositions();
    }

    // Import rows identify an existing person by name plus phone; a sole same-name
    // match can update a changed phone. Ambiguous names must not overwrite anyone.
    @Transactional(rollbackFor = Exception.class)
    public CompanyContact mergeImported(Long companyId, CompanyContact incoming) {
        return mergeImported(companyId, incoming, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanyContact mergeImported(Long companyId, CompanyContact incoming, boolean allowNameMatch) {
        lockCompany(companyId);
        normalize(incoming);
        List<CompanyContact> contacts = list(companyId);
        List<CompanyContact> exact = contacts.stream().filter(c -> c.getName().equals(incoming.getName())
                && c.getPhone().equals(incoming.getPhone())).toList();
        List<CompanyContact> sameName = contacts.stream().filter(c -> c.getName().equals(incoming.getName())).toList();
        CompanyContact match = !exact.isEmpty() ? exact.get(0) : allowNameMatch && sameName.size() == 1 ? sameName.get(0) : null;
        if (match != null) {
            incoming.setId(match.getId());
            if (incoming.getEmail().isEmpty()) incoming.setEmail(match.getEmail());
        }
        incoming.setCompanyId(companyId);
        if (incoming.getId() == null) mapper.insert(incoming); else mapper.update(incoming);
        invalidatePositions();
        return incoming;
    }

    private void normalize(CompanyContact contact) {
        contact.setName(contact.getName() == null ? "" : contact.getName().trim());
        contact.setPhone(contact.getPhone() == null ? "" : contact.getPhone().replaceAll("\\s+", ""));
        contact.setEmail(contact.getEmail() == null ? "" : contact.getEmail().replaceAll("\\s+", ""));
        if (contact.getName().isEmpty() || contact.getName().length() > 50
                || !contact.getPhone().matches("^[+0-9()（）\\-转]{7,30}$")
                || contact.getEmail().length() > 100
                || (!contact.getEmail().isEmpty() && !contact.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new BusinessException("请填写有效的联系人姓名、电话和邮箱（邮箱选填）");
        }
    }

    public void validateImported(CompanyContact contact) { normalize(contact); }

    public void invalidatePositions() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { positionCacheService.clearPositionsCache(); }
            });
        } else positionCacheService.clearPositionsCache();
    }
}
