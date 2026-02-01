# Database Migration Structure

This document explains the consolidated database migration structure for the iqscaffold-user-service.

## Migration Organization

### System Migrations (`system/`)
- **Purpose**: System-wide tables and data in the `public` schema
- **Scope**: Cross-tenant entities like tenants, organizations, authorities, organization preferences
- **Execution**: Once per database instance
- **Naming**: Timestamp-based IDs (YYYYMMDDHHMM00-description)

### Tenant Migrations (`tenant/`)
- **Purpose**: Tenant-specific tables and data in tenant schemas
- **Scope**: User data, user preferences, audit logs per tenant
- **Execution**: Once per tenant schema
- **Naming**: Timestamp-based IDs (YYYYMMDDHHMM00-description)

### Demo Data (`tenant/demo/`)
- **Purpose**: Sample data for development and testing
- **Context**: Only applied when `demo` context is active
- **Scope**: Test users, organizations, and preferences

## Migration Execution Order

1. **System migrations** (public schema setup)
   - 20251110154000: Tenants table and constraints
   - 20251110154100: Authorities table and system roles
   - 20251110154200: Tenant lifecycle status columns
   - 20251110154300: CRM-specific authorities
   - 20251110154600: Organizations and organization preferences (consolidated)
   - 20251110154700: Payment gateway capabilities

2. **Tenant migrations** (per-tenant schema setup)
   - 20251110154500: Users table and related entities
   - 20251122000100: User preferences
   - 20251210000000: Payment gateway abstraction

3. **Demo data** (if demo context enabled)
   - Sample tenant and organization
   - Test users with various authority levels
   - User and organization preferences for demo accounts

## Key Relationships

- `tenants` (public) ← `organizations` (public) [1:1]
- `organizations` (public) ← `organization_preferences` (public) [1:1]
- `users` (tenant) ← `user_preferences` (tenant) [1:1]
- `authorities` (public) ← `user_authorities` (tenant) ← `users` (tenant) [M:N]

## Consolidation Changes Made

1. ✅ **Consolidated organization preferences**: Moved all organization settings to public schema
2. ✅ **Standardized change set IDs**: All migrations now use timestamp-based naming (YYYYMMDDHHMM00)
3. ✅ **Fixed broken SQL**: Corrected tenant ID validation regex pattern
4. ✅ **Unified migration structure**: Created root master.xml for coordinated execution
5. ✅ **Enhanced organization preferences**: Added all fields from both versions (localization, security, notifications)
6. ✅ **Removed duplicates**: Eliminated redundant organization preferences table from tenant schema

## Schema Consistency Achieved

- **Consistent naming**: All migrations follow timestamp-based naming convention
- **Proper schema separation**: System-wide entities in public, tenant-specific in tenant schemas
- **Complete organization preferences**: Single comprehensive table with all necessary fields
- **Fixed foreign key relationships**: All references properly defined and validated
- **Optimized indexes**: Performance indexes added for all key lookup patterns

## Migration Files Structure

```
db/changelog/
├── master.xml                                    # Root orchestrator
├── system/
│   ├── master.xml                               # System migrations orchestrator
│   ├── 20251110154000-create-tenants-table.xml
│   ├── 20251110154100-create-authorities-table.xml
│   ├── 20251110154200-add-lifecycle-status-columns.xml
│   ├── 20251110154300-initialize-crm-authorities.xml
│   ├── 20251110154600-organizations-public-schema.xml
│   └── 20251110154700-add-organization-capabilities.xml
├── tenant/
│   ├── master.xml                               # Tenant migrations orchestrator
│   ├── 20251110154500-initial-user-schema.xml
│   ├── 20251122000100-user-preferences-schema.xml
│   ├── 20251210000000-payment-gateway-abstraction.xml
│   └── demo/
│       ├── master.xml                           # Demo data orchestrator
│       ├── 20250121000000-demo-users-with-authorities.xml
│       └── README.md
└── README.md                                    # This file
```