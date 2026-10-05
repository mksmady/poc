# Source to Liferay mapping (US-009)

Fill in once the source extract is known (open question 11).

| Source field | Liferay entity.field | Notes |
|---|---|---|
| category_code | TaxonomyCategory.externalReferenceCode | |
| parent_category_code | TaxonomyCategory.parentTaxonomyCategory | hierarchy |
| product_code | Product.externalReferenceCode | |
| name | Product.name.en_US | |
| sku | Sku.sku | |
| spec_* | ProductSpecification | additional criteria (US-007 AC4) |
