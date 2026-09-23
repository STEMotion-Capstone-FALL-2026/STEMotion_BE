-- Promote Long Tri to ADMIN so the developer session can navigate all studios and test permissions without 403
UPDATE users
SET role = 'ADMIN'
WHERE email = 'longtri@stemotion.vn';

UPDATE workspace_members
SET role = 'ADMIN'
WHERE invited_email = 'longtri@stemotion.vn';
